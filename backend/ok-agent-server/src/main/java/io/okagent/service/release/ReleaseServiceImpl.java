package io.okagent.service.release;

import io.okagent.domain.agent.AgentAsset;
import io.okagent.domain.channel.ChannelAsset;
import io.okagent.domain.release.AgentRelease;
import io.okagent.domain.release.AgentVersion;
import io.okagent.domain.release.ReleaseStatus;
import io.okagent.domain.release.ReleaseTargetType;
import io.okagent.repository.agent.AgentAssetRepository;
import io.okagent.repository.channel.ChannelAssetRepository;
import io.okagent.repository.release.AgentReleaseRepository;
import io.okagent.repository.release.AgentVersionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Transactional implementation of {@link ReleaseService}. Version numbers are allocated per agent
 * (1, 2, 3, ...) inside the create transaction. Publish/rollback mutate the channel pointer and
 * release statuses atomically; the channel's {@code @Version} optimistic lock guards concurrent
 * promotions.
 */
@Service
public class ReleaseServiceImpl implements ReleaseService {

    private final AgentAssetRepository agents;
    private final AgentVersionRepository versions;
    private final AgentReleaseRepository releases;
    private final ChannelAssetRepository channels;
    private final AgentSnapshotService snapshots;

    public ReleaseServiceImpl(
            AgentAssetRepository agents,
            AgentVersionRepository versions,
            AgentReleaseRepository releases,
            ChannelAssetRepository channels,
            AgentSnapshotService snapshots) {
        this.agents = agents;
        this.versions = versions;
        this.releases = releases;
        this.channels = channels;
        this.snapshots = snapshots;
    }

    @Override
    @Transactional
    public AgentVersion createVersion(UUID agentId, String label, String changelog, String actor) {
        AgentAsset draft = agents.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
        // Validate references (and recursively that every child agent already has a version to pin)
        // before allocating a version number, so a failed freeze does not burn a number.
        AgentSnapshotService.SnapshotBundle bundle = snapshots.buildSnapshot(draft);

        int nextNo = versions.findTopByAgentIdOrderByVersionNoDesc(agentId)
                .map(v -> v.getVersionNo() + 1)
                .orElse(1);
        UUID parentId = versions.findTopByAgentIdOrderByVersionNoDesc(agentId)
                .map(AgentVersion::getId)
                .orElse(null);

        AgentVersion version = new AgentVersion(
                UUID.randomUUID(),
                agentId,
                nextNo,
                normalize(label),
                bundle.snapshotJson(),
                bundle.contentHash(),
                parentId,
                changelog,
                actor);
        return versions.save(version);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentVersion> listVersions(UUID agentId) {
        return versions.findByAgentIdOrderByVersionNoDesc(agentId);
    }

    @Override
    @Transactional(readOnly = true)
    public AgentVersion getVersion(UUID versionId) {
        return versions.findById(versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found"));
    }

    @Override
    @Transactional
    public AgentRelease publishToChannel(UUID agentId, int versionNo, UUID channelId, String actor) {
        ChannelAsset channel = channels.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
        if (!agentId.equals(channel.getBoundAgentId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "该渠道绑定的是另一个 Agent，不能发布此 Agent 的版本");
        }
        AgentVersion version = versions.findByAgentIdAndVersionNo(agentId, versionNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定版本不存在"));

        // Supersede the currently promoted release on this channel, if any.
        releases.findByTargetTypeAndTargetIdAndStatus(
                        ReleaseTargetType.CHANNEL, channelId, ReleaseStatus.PROMOTED)
                .ifPresent(current -> {
                    current.markSuperseded();
                    releases.save(current);
                });

        AgentRelease release = new AgentRelease(
                UUID.randomUUID(),
                agentId,
                version.getId(),
                versionNo,
                ReleaseTargetType.CHANNEL,
                channelId,
                null,
                actor);
        releases.save(release);

        // Atomically move the channel pointer; previousReleaseId is retained for rollback.
        channel.promoteRelease(release.getId());
        channels.save(channel);
        return release;
    }

    @Override
    @Transactional
    public AgentRelease rollbackChannel(UUID channelId, String actor) {
        ChannelAsset channel = channels.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
        UUID previousId = channel.getPreviousReleaseId();
        if (previousId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有可回滚的上一个版本");
        }
        AgentRelease previous = releases.findById(previousId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "上一个版本记录不存在"));

        // Mark the currently promoted release as rolled back.
        AgentRelease current = releases
                .findByTargetTypeAndTargetIdAndStatus(ReleaseTargetType.CHANNEL, channelId, ReleaseStatus.PROMOTED)
                .orElse(null);
        UUID rolledBackFromId = current != null ? current.getId() : null;
        if (current != null) {
            current.markRolledBack();
            releases.save(current);
        }

        // Re-promote the previous release as a new PROMOTED record (preserving rollback lineage)
        // rather than mutating the historical row, so audit history is append-only.
        AgentRelease restored = new AgentRelease(
                UUID.randomUUID(),
                previous.getAgentId(),
                previous.getVersionId(),
                previous.getVersionNo(),
                ReleaseTargetType.CHANNEL,
                channelId,
                previous.getId(),
                actor);
        releases.save(restored);

        // current is restored; remember what we rolled back from so a second rollback can toggle back.
        channel.setReleasePointers(restored.getId(), rolledBackFromId);
        channels.save(channel);
        return restored;
    }

    @Override
    @Transactional(readOnly = true)
    public AgentRelease getCurrentRelease(UUID channelId) {
        return releases
                .findByTargetTypeAndTargetIdAndStatus(ReleaseTargetType.CHANNEL, channelId, ReleaseStatus.PROMOTED)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentRelease> listChannelReleases(UUID channelId) {
        return releases.findByTargetTypeAndTargetIdOrderByPublishedAtDesc(ReleaseTargetType.CHANNEL, channelId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentRelease> listAgentReleases(UUID agentId) {
        return releases.findByAgentIdOrderByPublishedAtDesc(agentId);
    }

    private static String normalize(String label) {
        if (label == null) return null;
        String trimmed = label.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
