package io.okagent.domain.dialogue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Structured business outcome distilled from one customer conversation. */
@Entity
@Table(name = "dialogue_outcome")
public class DialogueOutcome {
    @Id
    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "customer_need", columnDefinition = "TEXT")
    private String customerNeed;

    @Column(name = "intent_label", length = 128)
    private String intentLabel;

    @Column(name = "product_interest", length = 512)
    private String productInterest;

    @Column(name = "budget", length = 128)
    private String budget;

    @Column(name = "purchase_timeline", length = 128)
    private String purchaseTimeline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CustomerSentiment sentiment = CustomerSentiment.UNKNOWN;

    @Column(name = "resolution_code", length = 64)
    private String resolutionCode;

    @Column(name = "next_action", length = 512)
    private String nextAction;

    @Column(name = "follow_up_at")
    private Instant followUpAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected DialogueOutcome() {}

    public DialogueOutcome(String sessionId, Instant now) {
        this.sessionId = sessionId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Replaces editable structured fields and records the responsible console account. */
    public void revise(
            String summary,
            String customerNeed,
            String intentLabel,
            String productInterest,
            String budget,
            String purchaseTimeline,
            CustomerSentiment sentiment,
            String resolutionCode,
            String nextAction,
            Instant followUpAt,
            UUID actorId,
            Instant now) {
        this.summary = trimToNull(summary);
        this.customerNeed = trimToNull(customerNeed);
        this.intentLabel = trimToNull(intentLabel);
        this.productInterest = trimToNull(productInterest);
        this.budget = trimToNull(budget);
        this.purchaseTimeline = trimToNull(purchaseTimeline);
        this.sentiment = sentiment == null ? CustomerSentiment.UNKNOWN : sentiment;
        this.resolutionCode = trimToNull(resolutionCode);
        this.nextAction = trimToNull(nextAction);
        this.followUpAt = followUpAt;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String getSessionId() { return sessionId; }
    public String getSummary() { return summary; }
    public String getCustomerNeed() { return customerNeed; }
    public String getIntentLabel() { return intentLabel; }
    public String getProductInterest() { return productInterest; }
    public String getBudget() { return budget; }
    public String getPurchaseTimeline() { return purchaseTimeline; }
    public CustomerSentiment getSentiment() { return sentiment; }
    public String getResolutionCode() { return resolutionCode; }
    public String getNextAction() { return nextAction; }
    public Instant getFollowUpAt() { return followUpAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getRowVersion() { return rowVersion; }
}
