package io.okagent.module.observe.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.okagent.module.observe.domain.SpanStatus;
import io.okagent.module.observe.domain.SpanType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TurnTracePayloadLimitsTests {
    private final TracePayloadSanitizer sanitizer = new TracePayloadSanitizer();

    @Test
    void boundsToolInputAndStreamingOutput() {
        TurnTrace trace =
                TurnTrace.start("trace", "session", UUID.randomUUID(), "user", 1, "agent", sanitizer);
        String oversized = "x".repeat(TurnTrace.MAX_PAYLOAD_CHARS * 2);
        TurnTrace.MutableSpan tool =
                trace.startTool(SpanType.TOOL, "call", "tool", Map.of("payload", oversized));
        tool.appendOutput(oversized);
        tool.appendOutput(oversized);
        tool.finish(SpanStatus.OK, null, TurnTrace.microsNow());

        var persisted = trace.finish(SpanStatus.OK, null).stream()
                .filter(span -> span.getSpanType().equals(SpanType.TOOL.name()))
                .findFirst()
                .orElseThrow();

        assertThat(persisted.getInput())
                .hasSizeLessThanOrEqualTo(TurnTrace.MAX_PAYLOAD_CHARS)
                .endsWith("...[trace payload truncated]");
        assertThat(persisted.getOutput())
                .hasSizeLessThanOrEqualTo(TurnTrace.MAX_PAYLOAD_CHARS)
                .endsWith("...[trace payload truncated]");
    }

    @Test
    void doesNotSplitUnicodeSurrogatePairsAtTheLimit() {
        String marker = "\n...[trace payload truncated]";
        int retainedChars = TurnTrace.MAX_PAYLOAD_CHARS - marker.length();
        String prefix = "x".repeat(retainedChars - 1);

        String limited = TurnTrace.limitPayload(prefix + "😀" + "sensitive-tail".repeat(10));

        assertThat(limited).endsWith(marker);
        assertThat(Character.isSurrogate(limited.charAt(limited.length() - marker.length() - 1))).isFalse();
    }

    @Test
    void sanitizesToolPayloadsBeforeMaterializingSpans() {
        TurnTrace trace =
                TurnTrace.start("trace", "session", UUID.randomUUID(), "user", 1, "agent", sanitizer);
        TurnTrace.MutableSpan tool =
                trace.startTool(SpanType.TOOL, "call", "tool", Map.of("apiKey", "input-secret"));
        tool.appendOutput("Authorization: Bearer output-secret");
        tool.finish(SpanStatus.OK, null, TurnTrace.microsNow());

        var persisted = trace.finish(SpanStatus.OK, null).stream()
                .filter(span -> span.getSpanType().equals(SpanType.TOOL.name()))
                .findFirst()
                .orElseThrow();

        assertThat(persisted.getInput()).doesNotContain("input-secret").contains("[REDACTED]");
        assertThat(persisted.getOutput()).doesNotContain("output-secret").contains("Bearer [REDACTED]");
    }
}
