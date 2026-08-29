package io.okagent.module.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Customer satisfaction feedback associated one-to-one with a conversation. */
@Entity
@Table(name = "dialogue_satisfaction")
public class DialogueSatisfaction {
    @Id
    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String feedback;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected DialogueSatisfaction() {}

    public DialogueSatisfaction(String sessionId, int rating, String feedback, UUID updatedBy, Instant now) {
        this.sessionId = sessionId;
        this.createdAt = now;
        revise(rating, feedback, updatedBy, now);
    }

    /** Replaces the rating after validating the public five-point scale. */
    public void revise(int rating, String feedback, UUID updatedBy, Instant now) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("SATISFACTION_RATING_OUT_OF_RANGE");
        }
        this.rating = rating;
        this.feedback = feedback == null || feedback.isBlank() ? null : feedback.trim();
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public String getSessionId() { return sessionId; }
    public int getRating() { return rating; }
    public String getFeedback() { return feedback; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getRowVersion() { return rowVersion; }
}
