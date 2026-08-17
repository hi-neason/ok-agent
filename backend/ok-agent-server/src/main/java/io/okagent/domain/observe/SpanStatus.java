package io.okagent.domain.observe;

/** Outcome of a {@link TraceSpan}, aligned with OpenTelemetry status codes. */
public enum SpanStatus {
    OK,
    ERROR,
    CANCELLED
}
