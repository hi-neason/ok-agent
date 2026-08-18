package io.okagent.service.observe;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.mcp.McpTool;
import io.okagent.domain.observe.SpanStatus;
import io.okagent.domain.observe.SpanType;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

/**
 * Framework middleware that builds a full execution trace (agent / model / tool spans) for every
 * turn and hands the completed spans to a {@link TraceSink} for in-process persistence.
 *
 * <p>It is intentionally a stateless singleton: per-turn state ({@link TurnTrace}) travels
 * through the Reactor {@code Context}, so concurrent sessions/turns never interfere. Metadata the
 * runtime must supply (trace id, turn sequence, agent id) is read from {@link RuntimeContext}
 * attributes under the {@code OKAGENT_TRACE_*} keys; the middleware is a no-op for turns that do
 * not provide them.
 *
 * <p>Knowledge-base and workflow tools are standard {@code @Tool} methods, so their calls flow
 * through the same {@code onActing} hook as MCP / built-in tools and are captured automatically.
 *
 * <p>No HTTP or OTLP is involved: the sink calls an internal service that writes to MySQL.
 */
@Component
public class TraceCollectingMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(TraceCollectingMiddleware.class);

    /** RuntimeContext attribute carrying the trace id for the current turn. */
    public static final String CTX_TRACE_ID = "okagent.trace.id";
    /** RuntimeContext attribute carrying the dialogue turn sequence (1-based) for this turn. */
    public static final String CTX_TURN_SEQ = "okagent.trace.turnSeq";
    /** RuntimeContext attribute carrying the agent asset id (UUID string) for this turn. */
    public static final String CTX_AGENT_ID = "okagent.trace.agentId";

    private final TraceSink sink;

    public TraceCollectingMiddleware(TraceSink sink) {
        this.sink = sink;
    }

    @Override
    public int order() {
        // Run outermost so we measure the full turn including every other middleware.
        return 1000;
    }

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext ctx,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        String traceId = ctx.get(CTX_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            return next.apply(input);
        }
        UUID agentId = parseAgentId(ctx.get(CTX_AGENT_ID));
        int turnSeq = parseInt(ctx.get(CTX_TURN_SEQ), 0);
        TurnTrace trace =
                TurnTrace.start(
                        traceId,
                        ctx.getSessionId(),
                        agentId,
                        ctx.getUserId(),
                        turnSeq,
                        agent.getName());

        AtomicReference<SpanStatus> rootStatus = new AtomicReference<>(SpanStatus.OK);
        AtomicReference<String> rootError = new AtomicReference<>();

        return next.apply(input)
                .doOnError(
                        e -> {
                            rootStatus.set(SpanStatus.ERROR);
                            rootError.set(e.getMessage());
                        })
                .doOnCancel(() -> rootStatus.set(SpanStatus.CANCELLED))
                .doFinally(
                        signal -> {
                            try {
                                sink.saveAll(trace.finish(rootStatus.get(), rootError.get()));
                            } catch (Exception e) {
                                log.warn(
                                        "Failed to persist trace {}: {}",
                                        traceId,
                                        e.getMessage());
                            }
                        })
                // Seed the per-turn trace into the downstream Reactor context so inner onModelCall
                // /onActing hooks (which assemble inside this same pipeline) can read it via
                // ContextView, even across thread hops.
                .contextWrite(reactorCtx -> reactorCtx.put(TurnTrace.CONTEXT_KEY, trace));
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent,
            RuntimeContext ctx,
            ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {
        return Flux.deferContextual(
                contextView -> {
                    TurnTrace trace = traceFrom(contextView);
                    if (trace == null) {
                        return next.apply(input);
                    }
                    String modelName =
                            input.model() != null && input.model().getModelName() != null
                                    ? input.model().getModelName()
                                    : "unknown";
                    TurnTrace.MutableSpan span =
                            trace.startModel(
                                    modelName, input.messages(), input.tools(), input.options());

                    return next.apply(input)
                            .doOnNext(
                                    event -> {
                                        if (event instanceof TextBlockDeltaEvent delta) {
                                            span.appendOutput(delta.getDelta());
                                        } else if (event instanceof ThinkingBlockDeltaEvent thinking) {
                                            span.appendThinking(thinking.getDelta());
                                        } else if (event instanceof ModelCallEndEvent end
                                                && end.getUsage() != null) {
                                            var usage = end.getUsage();
                                            span.recordUsage(
                                                    usage.getInputTokens(),
                                                    usage.getOutputTokens(),
                                                    usage.getCachedTokens(),
                                                    usage.getTime());
                                        }
                                    })
                            .doOnError(e -> span.fail(e.getMessage()))
                            .doOnComplete(
                                    () -> span.finish(SpanStatus.OK, null, TurnTrace.microsNow()));
                });
    }

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent,
            RuntimeContext ctx,
            ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {
        return Flux.deferContextual(
                contextView -> {
                    TurnTrace trace = traceFrom(contextView);
                    if (trace == null
                            || input.toolCalls() == null
                            || input.toolCalls().isEmpty()) {
                        return next.apply(input);
                    }
                    // Pre-create a span for every tool call in this acting batch so parallel/async
                    // tools are covered even if a result event arrives without a preceding start.
                    for (ToolUseBlock toolCall : input.toolCalls()) {
                        SpanType type = classifyTool(agent, toolCall.getName());
                        trace.startTool(
                                type, toolCall.getId(), toolCall.getName(), toolCall.getInput());
                    }
                    return next.apply(input)
                            .doOnNext(
                                    event -> {
                                        if (event instanceof ToolResultStartEvent start) {
                                            TurnTrace.MutableSpan span =
                                                    trace.spanByCallId(start.getToolCallId());
                                            if (span != null
                                                    && start.getToolCallName() != null) {
                                                span.attribute(
                                                        "gen_ai.tool.name",
                                                        start.getToolCallName());
                                            }
                                        } else if (event instanceof ToolResultTextDeltaEvent delta) {
                                            TurnTrace.MutableSpan span =
                                                    trace.spanByCallId(delta.getToolCallId());
                                            if (span != null) {
                                                span.appendOutput(delta.getDelta());
                                            }
                                        } else if (event instanceof ToolResultEndEvent end) {
                                            TurnTrace.MutableSpan span =
                                                    trace.spanByCallId(end.getToolCallId());
                                            if (span != null) {
                                                SpanStatus status = mapToolState(end);
                                                span.attribute(
                                                        "gen_ai.tool.result_state",
                                                        end.getState() != null
                                                                ? end.getState().name()
                                                                : "UNKNOWN");
                                                span.finish(
                                                        status, null, TurnTrace.microsNow());
                                                // Tools catch exceptions and return error strings
                                                // with state SUCCESS; downgrade span to ERROR when
                                                // the output starts with an error prefix.
                                                span.markErrorIfToolFailed();
                                            }
                                        }
                                    })
                            .doOnError(
                                    e -> {
                                        for (ToolUseBlock toolCall : input.toolCalls()) {
                                            TurnTrace.MutableSpan span =
                                                    trace.spanByCallId(toolCall.getId());
                                            if (span != null) {
                                                span.fail(e.getMessage());
                                            }
                                        }
                                    });
                });
    }

    private static TurnTrace traceFrom(ContextView view) {
        // Inner onModelCall/onActing hooks run inside the onAgent pipeline, whose contextWrite
        // seeded the per-turn trace. Context propagates from downstream (onAgent) to upstream
        // (inner hooks), exactly like OtelTracingMiddleware relies on for its spans.
        return view.getOrDefault(TurnTrace.CONTEXT_KEY, null);
    }

    private static SpanStatus mapToolState(ToolResultEndEvent event) {
        if (event.getState() == null) {
            return SpanStatus.OK;
        }
        return switch (event.getState()) {
            case SUCCESS, RUNNING -> SpanStatus.OK;
            case ERROR, INTERRUPTED -> SpanStatus.ERROR;
            case DENIED -> SpanStatus.CANCELLED;
        };
    }

    /**
     * ok-agent external workflow tools (see {@code WorkflowTools}). Fixed names, registered
     * programmatically on the agent toolkit.
     */
    private static final Set<String> WORKFLOW_TOOL_NAMES =
            Set.of("list_workflows", "describe_workflow", "start_workflow");

    /**
     * ok-agent knowledge-base / RAG tools (see {@code KnowledgeTools}). Fixed names, registered
     * programmatically on the agent toolkit.
     */
    private static final Set<String> RAG_TOOL_NAMES =
            Set.of("list_knowledge_bases", "search_knowledge");

    /**
     * Harness-native skill tools. These surface skills to the model (load/propose/manage); the
     * actual skill body then runs through ordinary file/shell tools.
     */
    private static final Set<String> SKILL_TOOL_NAMES =
            Set.of("load_skill_through_path", "use_skill", "propose_skill", "skill_manage");

    /**
     * Classifies a tool call into its source bucket so the waterfall can distinguish MCP, skill,
     * workflow and RAG calls from ordinary built-in tools.
     *
     * <p>Our own workflow/RAG tools and the harness skill tools have fixed well-known names and
     * are matched first. MCP tools are recognised by resolving the registered {@link AgentTool}
     * and checking for the {@link McpTool} type (MCP server tools carry no naming convention, so
     * name matching is unreliable). Anything else falls back to {@link SpanType#TOOL}.
     */
    private static SpanType classifyTool(Agent agent, String toolName) {
        if (toolName == null) {
            return SpanType.TOOL;
        }
        if (WORKFLOW_TOOL_NAMES.contains(toolName)) {
            return SpanType.WORKFLOW;
        }
        if (RAG_TOOL_NAMES.contains(toolName)) {
            return SpanType.RAG;
        }
        if (SKILL_TOOL_NAMES.contains(toolName)) {
            return SpanType.SKILL;
        }
        if (agent != null && agent.getToolkit() != null) {
            try {
                AgentTool registered = agent.getToolkit().getTool(toolName);
                if (registered instanceof McpTool) {
                    return SpanType.MCP;
                }
            } catch (Exception ignored) {
                // Toolkit lookup should not fail trace collection; fall through to TOOL.
            }
        }
        return SpanType.TOOL;
    }

    private static UUID parseAgentId(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int parseInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
