package io.okagent.module.observe.application;

import io.agentscope.core.message.Msg;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.util.JsonUtils;
import io.okagent.module.observe.domain.SpanStatus;
import io.okagent.module.observe.domain.SpanType;
import io.okagent.module.observe.domain.TraceSpan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable, per-turn accumulator for the spans of one agent invocation.
 *
 * <p>A single instance lives in the Reactor {@code Context} for the lifetime of the turn (see
 * {@link TraceCollectingMiddleware}). The root AGENT span is opened when the turn starts and closed
 * when the event stream terminates; MODEL and TOOL child spans are appended by the corresponding
 * middleware hooks. The instance is not shared across turns.
 */
final class TurnTrace {

    static final String CONTEXT_KEY = "okagent.trace";
    static final int MAX_PAYLOAD_CHARS = 64 * 1024;
    static final int MAX_ERROR_CHARS = 4 * 1024;
    private static final String TRUNCATED = "\n...[trace payload truncated]";

    private final String traceId;
    private final String rootSpanId;
    private final String sessionId;
    private final java.util.UUID agentId;
    private final String userId;
    private final int turnSeq;
    private final String agentName;
    private final TracePayloadSanitizer sanitizer;
    private final long startUs;

    private final Map<String, MutableSpan> spansById = new ConcurrentHashMap<>();
    private final List<MutableSpan> ordered = new ArrayList<>();

    private TurnTrace(
            String traceId,
            String sessionId,
            UUID agentId,
            String userId,
            int turnSeq,
            String agentName,
            TracePayloadSanitizer sanitizer,
            long startUs) {
        this.traceId = traceId;
        this.rootSpanId = randomSpanId();
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.userId = userId;
        this.turnSeq = turnSeq;
        this.agentName = agentName;
        this.sanitizer = sanitizer;
        this.startUs = startUs;
    }

    /**
     * Starts a new turn trace rooted at an AGENT span. Metadata (trace/session/turn) is supplied by
     * the runtime so the same middleware works for the debug runtime and future runtime instances.
     */
    static TurnTrace start(
            String traceId,
            String sessionId,
            UUID agentId,
            String userId,
            int turnSeq,
            String agentName,
            TracePayloadSanitizer sanitizer) {
        long now = microsNow();
        TurnTrace trace = new TurnTrace(traceId, sessionId, agentId, userId, turnSeq, agentName, sanitizer, now);
        MutableSpan root = new MutableSpan(
                trace.rootSpanId, null, SpanType.AGENT, "invoke_agent " + agentName, now, sanitizer);
        trace.register(root);
        return trace;
    }

    String getTraceId() {
        return traceId;
    }

    String getRootSpanId() {
        return rootSpanId;
    }

    /** Opens a child MODEL span under the root, capturing a bounded request payload preview. */
    MutableSpan startModel(String modelName, List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        MutableSpan span = new MutableSpan(
                randomSpanId(), rootSpanId, SpanType.MODEL, "chat " + modelName, microsNow(), sanitizer);
        span.attribute("gen_ai.request.model", modelName);
        span.attribute("gen_ai.request.messages.count", messages == null ? 0 : messages.size());
        span.attribute("gen_ai.request.tools.count", tools == null ? 0 : tools.size());
        if (options != null) {
            if (options.getTemperature() != null) {
                span.attribute("gen_ai.request.temperature", options.getTemperature());
            }
            if (options.getMaxTokens() != null) {
                span.attribute("gen_ai.request.max_tokens", options.getMaxTokens());
            }
        }
        // Capture enough request context for diagnostics without allowing an unbounded trace row.
        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("model", modelName);
        requestPayload.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            requestPayload.put("tools", tools);
        }
        if (options != null) {
            requestPayload.put("options", options);
        }
        span.input = limitPayload(sanitizer.sanitize(codec().toJson(requestPayload)));
        register(span);
        return span;
    }

    /** Opens a child tool span under the root. {@code type} classifies the tool source. */
    MutableSpan startTool(SpanType type, String callId, String toolName, Map<String, Object> inputArgs) {
        MutableSpan span = new MutableSpan(
                // Reuse the tool call id when present so tool result events can correlate.
                callId != null && !callId.isBlank() ? stableSpanId(callId) : randomSpanId(),
                rootSpanId,
                type,
                "execute_tool " + toolName,
                microsNow(),
                sanitizer);
        span.attribute("gen_ai.tool.name", toolName);
        span.attribute("gen_ai.tool.type", type.name());
        if (callId != null) {
            span.attribute("gen_ai.tool.call.id", callId);
        }
        span.input = limitPayload(sanitizer.sanitize(toJson(inputArgs)));
        register(span);
        return span;
    }

    MutableSpan spanByCallId(String callId) {
        if (callId == null) {
            return null;
        }
        return spansById.get(stableSpanId(callId));
    }

    private void register(MutableSpan span) {
        spansById.put(span.spanId, span);
        ordered.add(span);
    }

    /** Finalizes the root span and materializes all spans for persistence. */
    List<TraceSpan> finish(SpanStatus rootStatus, String errorMessage) {
        long endUs = microsNow();
        MutableSpan root = spansById.get(rootSpanId);
        if (root != null) {
            root.finish(rootStatus, errorMessage, endUs);
        }
        List<TraceSpan> result = new ArrayList<>(ordered.size());
        for (MutableSpan s : ordered) {
            if (s.endUs == 0) {
                s.finish(SpanStatus.CANCELLED, null, endUs);
            }
            result.add(new TraceSpan(
                    traceId,
                    s.spanId,
                    s.parentSpanId,
                    sessionId,
                    agentId,
                    userId,
                    turnSeq,
                    s.type,
                    s.name,
                    s.startUs,
                    s.endUs,
                    s.status,
                    s.attributesJson(),
                    s.input,
                    s.output));
        }
        return result;
    }

    static long microsNow() {
        java.time.Instant now = java.time.Instant.now();
        return now.getEpochSecond() * 1_000_000L + now.getNano() / 1000L;
    }

    static String randomSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String stableSpanId(String callId) {
        // Deterministic 16-char id derived from the tool call id, without leaking the raw id beyond
        // attributes.
        String hex = Integer.toHexString(callId.hashCode());
        StringBuilder sb = new StringBuilder("t");
        while (sb.length() + hex.length() < 16) {
            sb.append('0');
        }
        sb.append(hex);
        return sb.substring(0, 16);
    }

    static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return codec().toJson(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    static String limitPayload(String value) {
        return limit(value, MAX_PAYLOAD_CHARS, TRUNCATED);
    }

    private static String limitError(String value) {
        return limit(value, MAX_ERROR_CHARS, "...[truncated]");
    }

    private static String limit(String value, int maxChars, String marker) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        int end = maxChars - marker.length();
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end) + marker;
    }

    private static io.agentscope.core.util.JsonCodec codec() {
        return JsonUtils.getJsonCodec();
    }

    /** A span being built during the turn. Methods are safe to call from reactive doOn callbacks. */
    static final class MutableSpan {
        final String spanId;
        final String parentSpanId;
        final SpanType type;
        final String name;
        final long startUs;
        final TracePayloadSanitizer sanitizer;
        final Map<String, Object> attributes = new LinkedHashMap<>();
        final StringBuilder outputBuffer = new StringBuilder();
        final StringBuilder thinkingBuffer = new StringBuilder();
        boolean outputTruncated;
        boolean thinkingTruncated;
        long endUs;
        SpanStatus status = SpanStatus.OK;
        String input;
        String output;
        private String errorMessage;

        MutableSpan(
                String spanId,
                String parentSpanId,
                SpanType type,
                String name,
                long startUs,
                TracePayloadSanitizer sanitizer) {
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
            this.type = type;
            this.name = name;
            this.startUs = startUs;
            this.sanitizer = sanitizer;
        }

        void attribute(String key, Object value) {
            if (value != null) {
                attributes.put(key, value);
            }
        }

        void appendOutput(String chunk) {
            outputTruncated |= appendLimited(outputBuffer, chunk);
        }

        void appendThinking(String chunk) {
            thinkingTruncated |= appendLimited(thinkingBuffer, chunk);
        }

        void recordUsage(int inputTokens, int outputTokens, int cachedTokens, double latency) {
            attribute("gen_ai.usage.input_tokens", inputTokens);
            attribute("gen_ai.usage.output_tokens", outputTokens);
            attribute("gen_ai.usage.total_tokens", inputTokens + outputTokens);
            if (cachedTokens > 0) {
                attribute("gen_ai.usage.cached_tokens", cachedTokens);
            }
            if (latency > 0) {
                attribute("gen_ai.usage.time_ms", Math.round(latency * 1000));
            }
        }

        void fail(String message) {
            this.status = SpanStatus.ERROR;
            this.errorMessage = limitError(sanitizer.sanitize(message));
        }

        /**
         * Tools (knowledge/workflow/MCP) catch their own exceptions and return a human-readable
         * string starting with "Error" / "Workflow failed" instead of throwing, so the framework
         * reports ToolResult state SUCCESS. Inspect the captured output to downgrade the span to
         * ERROR when the tool actually returned an error message, so the trace reflects reality.
         *
         * <p>The framework JSON-serializes a String-returning tool result, so the captured output
         * may be surrounded by literal double quotes (e.g. {@code "Error: ..."}); strip a leading
         * quote before prefix matching.
         */
        void markErrorIfToolFailed() {
            if (status != SpanStatus.OK) {
                return;
            }
            String text = this.output;
            if (text == null || text.isBlank()) {
                text = outputBuffer.toString();
            }
            String trimmed = text.stripLeading();
            if (trimmed.startsWith("\"")) {
                trimmed = trimmed.substring(1).stripLeading();
            }
            if (trimmed.startsWith("Error")
                    || trimmed.startsWith("Workflow failed")
                    || trimmed.startsWith("Failed to ")) {
                this.status = SpanStatus.ERROR;
                this.errorMessage = limitError(
                        sanitizer.sanitize(trimmed.lines().findFirst().orElse("Tool returned an error")));
                attribute("error.message", this.errorMessage);
            }
        }

        void finish(SpanStatus status, String errorMessage, long endUs) {
            if (this.endUs != 0) {
                return;
            }
            this.endUs = endUs;
            if (status != null) {
                this.status = status;
            }
            if (errorMessage != null) {
                this.errorMessage = limitError(sanitizer.sanitize(errorMessage));
                attribute("error.message", this.errorMessage);
            }
            if (output == null) {
                // For model spans, combine any reasoning/thinking text and the visible reply so the
                // stored output is self-contained for replay.
                StringBuilder combined = new StringBuilder();
                if (thinkingBuffer.length() > 0) {
                    combined.append("<thinking>\n").append(thinkingBuffer).append("\n</thinking>\n\n");
                    if (thinkingTruncated) combined.append(TRUNCATED).append("\n\n");
                }
                if (outputBuffer.length() > 0) {
                    combined.append(outputBuffer);
                    if (outputTruncated) combined.append(TRUNCATED);
                }
                if (combined.length() > 0) {
                    this.output = limitPayload(sanitizer.sanitize(combined.toString()));
                }
            }
        }

        private String attributesJson() {
            if (attributes.isEmpty()) {
                return null;
            }
            return limitPayload(sanitizer.sanitize(toJson(attributes)));
        }

        private static boolean appendLimited(StringBuilder target, String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return false;
            }
            int remaining = MAX_PAYLOAD_CHARS - target.length();
            if (remaining <= 0) {
                return true;
            }
            if (chunk.length() <= remaining) {
                target.append(chunk);
                return false;
            }
            int end = remaining;
            if (end > 0 && Character.isHighSurrogate(chunk.charAt(end - 1))) {
                end--;
            }
            target.append(chunk, 0, end);
            return true;
        }
    }
}
