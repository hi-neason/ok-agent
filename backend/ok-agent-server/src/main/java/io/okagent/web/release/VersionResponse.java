package io.okagent.web.release;

import io.okagent.domain.release.AgentVersion;
import java.time.Instant;
import java.util.UUID;

/** Summary view of an immutable agent version. The full snapshot is omitted from lists and
 *  available via the single-version endpoint to keep payloads small. */
public record VersionResponse(
        UUID id,
        UUID agentId,
        int versionNo,
        String versionLabel,
        String contentHash,
        UUID parentVersionId,
        String changelog,
        String createdBy,
        Instant createdAt) {

    public static VersionResponse from(AgentVersion v) {
        return new VersionResponse(
                v.getId(),
                v.getAgentId(),
                v.getVersionNo(),
                v.getVersionLabel(),
                v.getContentHash(),
                v.getParentVersionId(),
                v.getChangelog(),
                v.getCreatedBy(),
                v.getCreatedAt());
    }
}
