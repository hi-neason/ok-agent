package io.okagent.module.channel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Current availability of one human operator across all assigned channels. */
@Entity
@Table(name = "operator_presence")
public class OperatorPresence {
    @Id
    @Column(name = "operator_account_id")
    private UUID operatorAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OperatorPresenceStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected OperatorPresence() {}

    public OperatorPresence(UUID operatorAccountId) {
        this.operatorAccountId = operatorAccountId;
        this.status = OperatorPresenceStatus.OFFLINE;
        this.updatedAt = Instant.now();
    }

    public void changeTo(OperatorPresenceStatus status) {
        this.status = java.util.Objects.requireNonNull(status, "status");
        this.updatedAt = Instant.now();
    }

    public UUID getOperatorAccountId() { return operatorAccountId; }
    public OperatorPresenceStatus getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
}
