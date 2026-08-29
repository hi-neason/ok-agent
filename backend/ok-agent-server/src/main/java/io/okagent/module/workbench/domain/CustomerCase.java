package io.okagent.module.workbench.domain;

import io.okagent.module.conversation.domain.DialoguePriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Lead or support ticket traceably created from a conversation. */
@Entity
@Table(name = "customer_case")
public class CustomerCase {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CustomerCaseType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CustomerCaseStatus status;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "customer_user_id", length = 128)
    private String customerUserId;

    @Column(name = "source_session_id", nullable = false, length = 64)
    private String sourceSessionId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DialoguePriority priority;

    @Column(name = "owner_account_id")
    private UUID ownerAccountId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected CustomerCase() {}

    public CustomerCase(
            UUID id,
            CustomerCaseType type,
            String title,
            String customerUserId,
            String sourceSessionId,
            String description,
            DialoguePriority priority,
            UUID ownerAccountId,
            UUID createdBy,
            Instant now) {
        this.id = id;
        this.type = type;
        this.status = type == CustomerCaseType.LEAD ? CustomerCaseStatus.NEW : CustomerCaseStatus.OPEN;
        this.title = title;
        this.customerUserId = customerUserId;
        this.sourceSessionId = sourceSessionId;
        this.description = description;
        this.priority = priority;
        this.ownerAccountId = ownerAccountId;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public CustomerCaseType getType() { return type; }
    public CustomerCaseStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public String getCustomerUserId() { return customerUserId; }
    public String getSourceSessionId() { return sourceSessionId; }
    public String getDescription() { return description; }
    public DialoguePriority getPriority() { return priority; }
    public UUID getOwnerAccountId() { return ownerAccountId; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getRowVersion() { return rowVersion; }
}
