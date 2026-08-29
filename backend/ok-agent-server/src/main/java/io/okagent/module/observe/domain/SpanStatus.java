package io.okagent.module.observe.domain;

/** Outcome of a {@link TraceSpan}, aligned with OpenTelemetry status codes. */
public enum SpanStatus {
    OK,
    ERROR,
    CANCELLED
}
