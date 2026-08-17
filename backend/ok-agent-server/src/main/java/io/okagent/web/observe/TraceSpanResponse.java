package io.okagent.web.observe;

import io.okagent.domain.observe.TraceSpan;

/** Flat, JSON-friendly projection of one persisted span, ordered as captured. */
public record TraceSpanResponse(
        String spanId,
        String parentSpanId,
        String type,
        String name,
        long startUs,
        long endUs,
        long durationUs,
        String status,
        String attributes,
        String input,
        String output) {

    static TraceSpanResponse from(TraceSpan span) {
        return new TraceSpanResponse(
                span.getSpanId(),
                span.getParentSpanId(),
                span.getSpanType(),
                span.getName(),
                span.getStartUs(),
                span.getEndUs(),
                span.getDurationUs(),
                span.getStatus(),
                span.getAttributes(),
                span.getInput(),
                span.getOutput());
    }
}
