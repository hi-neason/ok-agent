package io.okagent.module.agentmanager.api;

import io.okagent.module.release.domain.AgentRelease;
import io.okagent.module.release.domain.ReleaseStatus;
import io.okagent.module.release.domain.ReleaseTargetType;
import java.time.Instant;
import java.util.UUID;

/** View of a deployment record (one version promoted onto a target). */
public record ReleaseResponse(
        UUID id,
        UUID agentId,
        UUID versionId,
        int versionNo,
        ReleaseTargetType targetType,
        UUID targetId,
        ReleaseStatus status,
        UUID rollbackOfId,
        String publishedBy,
        Instant publishedAt,
        Instant supersededAt) {

    public static ReleaseResponse from(AgentRelease r) {
        return new ReleaseResponse(
                r.getId(),
                r.getAgentId(),
                r.getVersionId(),
                r.getVersionNo(),
                r.getTargetType(),
                r.getTargetId(),
                r.getStatus(),
                r.getRollbackOfId(),
                r.getPublishedBy(),
                r.getPublishedAt(),
                r.getSupersededAt());
    }
}
