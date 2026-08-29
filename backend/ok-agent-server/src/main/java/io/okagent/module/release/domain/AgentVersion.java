package io.okagent.module.release.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable, numbered snapshot of an Agent's configuration frozen at a point in time. Once
 * created the row never changes: it carries the full {@code snapshot_json} and a SHA-256
 * {@code content_hash} used for de-duplication, drift detection and session-cache invalidation.
 *
 * <p>This is the "artifact" that gets deployed. The runtime plane resolves it through an
 * {@link AgentRelease} and never reads the editable {@code agent_asset} draft.
 */
@Entity
@Table(name = "agent_version")
public class AgentVersion {

    @Id
    private UUID id;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    /** Monotonically increasing per agent (1, 2, 3, ...), presented to users as "v1/v2/v3". */
    @Column(name = "version_no", nullable = false)
    private int versionNo;

    /** Optional human label / tag, e.g. "v1.0.0" or "hotfix-promo". */
    @Column(name = "version_label", length = 128)
    private String versionLabel;

    /** The frozen, self-contained configuration document. */
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "LONGTEXT")
    private String snapshotJson;

    /** SHA-256 hex of the canonical snapshot JSON. */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "parent_version_id")
    private UUID parentVersionId;

    @Column(columnDefinition = "TEXT")
    private String changelog;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AgentVersion() {}

    public AgentVersion(
            UUID id,
            UUID agentId,
            int versionNo,
            String versionLabel,
            String snapshotJson,
            String contentHash,
            UUID parentVersionId,
            String changelog,
            String createdBy) {
        this.id = id;
        this.agentId = agentId;
        this.versionNo = versionNo;
        this.versionLabel = versionLabel;
        this.snapshotJson = snapshotJson;
        this.contentHash = contentHash;
        this.parentVersionId = parentVersionId;
        this.changelog = changelog;
        this.createdBy = createdBy == null || createdBy.isBlank() ? "system" : createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAgentId() {
        return agentId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public String getContentHash() {
        return contentHash;
    }

    public UUID getParentVersionId() {
        return parentVersionId;
    }

    public String getChangelog() {
        return changelog;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
