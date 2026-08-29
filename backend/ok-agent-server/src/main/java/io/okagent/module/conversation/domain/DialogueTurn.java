package io.okagent.module.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * One persisted exchange (user message, agent reply, or an error note) inside a
 * {@link DialogueSession}. Role values are intentionally open (user / assistant / error) so the
 * store works for any producer, not only the debug runtime.
 */
@Entity
@Table(
        name = "dialogue_turn",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dialogue_turn_session_seq", columnNames = {"session_id", "seq"}))
public class DialogueTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(length = 128)
    private String model;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "token_usage")
    private Integer tokenUsage;

    /** Identifier of the execution trace captured for an assistant turn; null for user/error rows. */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DialogueTurn() {}

    public DialogueTurn(
            String sessionId,
            int seq,
            String role,
            String content,
            String model,
            Integer latencyMs,
            Instant createdAt) {
        this(sessionId, seq, role, content, model, latencyMs, null, createdAt);
    }

    public DialogueTurn(
            String sessionId,
            int seq,
            String role,
            String content,
            String model,
            Integer latencyMs,
            String traceId,
            Instant createdAt) {
        this.sessionId = sessionId;
        this.seq = seq;
        this.role = role;
        this.content = content;
        this.model = model;
        this.latencyMs = latencyMs;
        this.traceId = traceId;
        this.createdAt = createdAt;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getTraceId() {
        return traceId;
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getSeq() {
        return seq;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getModel() {
        return model;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public Integer getTokenUsage() {
        return tokenUsage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
