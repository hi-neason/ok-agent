package io.okagent.module.channel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Grants one interactive console account permission to handle human handoffs for one channel. */
@Entity
@Table(name = "channel_operator_assignment")
public class ChannelOperatorAssignment {
    @Id
    private UUID id;

    @Column(name = "channel_id", nullable = false)
    private UUID channelId;

    @Column(name = "operator_account_id", nullable = false)
    private UUID operatorAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    protected ChannelOperatorAssignment() {}

    public ChannelOperatorAssignment(UUID id, UUID channelId, UUID operatorAccountId, UUID createdBy) {
        this.id = id;
        this.channelId = channelId;
        this.operatorAccountId = operatorAccountId;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getChannelId() { return channelId; }
    public UUID getOperatorAccountId() { return operatorAccountId; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
}
