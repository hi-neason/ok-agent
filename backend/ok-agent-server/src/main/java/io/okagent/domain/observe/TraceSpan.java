package io.okagent.domain.observe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One node in the execution trace of a dialogue turn. A turn is a trace: the root
 * {@link SpanType#AGENT} span has child {@link SpanType#MODEL} and {@link SpanType#TOOL} spans that
 * reflect the ReAct loop (LLM call, tool execution, LLM call, ...). Knowledge-base and workflow
 * tools are regular tools, so they appear as TOOL spans without special handling.
 *
 * <p>{@code attributes} holds structured, small-cardinality metadata (model name, token usage, tool
 * call id, MCP server). {@code input} / {@code output} hold verbatim payloads (tool arguments, tool
 * result text; for model spans the request/response summaries) for full replay.
 */
@Entity
@Table(name = "trace_span")
public class TraceSpan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "span_id", nullable = false, length = 64)
    private String spanId;

    @Column(name = "parent_span_id", length = 64)
    private String parentSpanId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "agent_id")
    private UUID agentId;

    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(name = "turn_seq", nullable = false)
    private int turnSeq;

    @Column(name = "span_type", nullable = false, length = 16)
    private String spanType;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "start_us", nullable = false)
    private long startUs;

    @Column(name = "end_us", nullable = false)
    private long endUs;

    @Column(name = "duration_us", nullable = false)
    private long durationUs;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(columnDefinition = "JSON")
    private String attributes;

    @Column(columnDefinition = "LONGTEXT")
    private String input;

    @Column(columnDefinition = "LONGTEXT")
    private String output;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public TraceSpan() {}

    public TraceSpan(
            String traceId,
            String spanId,
            String parentSpanId,
            String sessionId,
            UUID agentId,
            String userId,
            int turnSeq,
            SpanType spanType,
            String name,
            long startUs,
            long endUs,
            SpanStatus status,
            String attributesJson,
            String input,
            String output) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.userId = userId;
        this.turnSeq = turnSeq;
        this.spanType = spanType.name();
        this.name = name;
        this.startUs = startUs;
        this.endUs = endUs;
        this.durationUs = endUs - startUs;
        this.status = status.name();
        this.attributes = attributesJson;
        this.input = input;
        this.output = output;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public UUID getAgentId() {
        return agentId;
    }

    public String getUserId() {
        return userId;
    }

    public int getTurnSeq() {
        return turnSeq;
    }

    public String getSpanType() {
        return spanType;
    }

    public String getName() {
        return name;
    }

    public long getStartUs() {
        return startUs;
    }

    public long getEndUs() {
        return endUs;
    }

    public long getDurationUs() {
        return durationUs;
    }

    public String getStatus() {
        return status;
    }

    public String getAttributes() {
        return attributes;
    }

    public String getInput() {
        return input;
    }

    public String getOutput() {
        return output;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
