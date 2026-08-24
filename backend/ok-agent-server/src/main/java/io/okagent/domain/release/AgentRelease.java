package io.okagent.domain.release;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A deployment record: a specific {@link AgentVersion} promoted onto a target (currently a
 * channel). The row is append-only except for {@link #status} and {@link #supersededAt}, which
 * move through the {@link ReleaseStatus} state machine. Publishing a newer version supersedes the
 * previous one; rollback marks the current release ROLLED_BACK rather than deleting history.
 */
@Entity
@Table(name = "agent_release")
public class AgentRelease {

    @Id
    private UUID id;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private ReleaseTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReleaseStatus status;

    /** When this record was created by a rollback, points at the release it reverted. */
    @Column(name = "rollback_of_id")
    private UUID rollbackOfId;

    @Column(name = "published_by", nullable = false, length = 64)
    private String publishedBy;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    protected AgentRelease() {}

    public AgentRelease(
            UUID id,
            UUID agentId,
            UUID versionId,
            int versionNo,
            ReleaseTargetType targetType,
            UUID targetId,
            UUID rollbackOfId,
            String publishedBy) {
        this.id = id;
        this.agentId = agentId;
        this.versionId = versionId;
        this.versionNo = versionNo;
        this.targetType = targetType;
        this.targetId = targetId;
        this.status = ReleaseStatus.PROMOTED;
        this.rollbackOfId = rollbackOfId;
        this.publishedBy = publishedBy == null || publishedBy.isBlank() ? "system" : publishedBy;
        this.publishedAt = Instant.now();
    }

    /** Marks this release as no longer serving its target because another release took over. */
    public void markSuperseded() {
        this.status = ReleaseStatus.SUPERSEDED;
        this.supersededAt = Instant.now();
    }

    /** Marks this release as reverted by a rollback. */
    public void markRolledBack() {
        this.status = ReleaseStatus.ROLLED_BACK;
        this.supersededAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAgentId() {
        return agentId;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public ReleaseTargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public ReleaseStatus getStatus() {
        return status;
    }

    public UUID getRollbackOfId() {
        return rollbackOfId;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }
}
