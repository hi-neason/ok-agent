package io.okagent.module.agentmanager.api;

import io.okagent.domain.release.AgentVersion;
import java.time.Instant;
import java.util.UUID;

/** Full view of one version, including the frozen snapshot JSON for read-only inspection. */
public record VersionDetailResponse(
        UUID id,
        UUID agentId,
        int versionNo,
        String versionLabel,
        String snapshotJson,
        String contentHash,
        UUID parentVersionId,
        String changelog,
        String createdBy,
        Instant createdAt) {

    public static VersionDetailResponse from(AgentVersion v) {
        return new VersionDetailResponse(
                v.getId(),
                v.getAgentId(),
                v.getVersionNo(),
                v.getVersionLabel(),
                v.getSnapshotJson(),
                v.getContentHash(),
                v.getParentVersionId(),
                v.getChangelog(),
                v.getCreatedBy(),
                v.getCreatedAt());
    }
}
