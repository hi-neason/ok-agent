package io.okagent.service.release;

import io.okagent.domain.channel.ChannelAsset;
import io.okagent.domain.release.ReleaseStatus;
import io.okagent.domain.release.ReleaseTargetType;
import io.okagent.repository.release.AgentReleaseRepository;
import io.okagent.repository.release.AgentVersionRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReleasedChannelAgentResolverImpl implements ReleasedChannelAgentResolver {

    private final AgentReleaseRepository releases;
    private final AgentVersionRepository versions;

    public ReleasedChannelAgentResolverImpl(AgentReleaseRepository releases, AgentVersionRepository versions) {
        this.releases = releases;
        this.versions = versions;
    }

    @Override
    @Transactional(readOnly = true)
    public ReleasedChannelAgent resolve(ChannelAsset channel) {
        if (channel.getCurrentReleaseId() == null) {
            throw new IllegalStateException("Channel '" + channel.getChannelKey() + "' has no promoted release");
        }
        var release = releases.findById(channel.getCurrentReleaseId())
                .orElseThrow(() -> new IllegalStateException(
                        "Promoted release " + channel.getCurrentReleaseId() + " was not found"));
        if (release.getTargetType() != ReleaseTargetType.CHANNEL
                || !channel.getId().equals(release.getTargetId())
                || release.getStatus() != ReleaseStatus.PROMOTED) {
            throw new IllegalStateException(
                    "Release " + release.getId() + " is not promoted on channel " + channel.getId());
        }
        if (!Objects.equals(channel.getBoundAgentId(), release.getAgentId())) {
            throw new IllegalStateException(
                    "Promoted release agent does not match channel binding for " + channel.getId());
        }
        var version = versions.findById(release.getVersionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Released Agent version " + release.getVersionId() + " was not found"));
        if (!release.getAgentId().equals(version.getAgentId()) || release.getVersionNo() != version.getVersionNo()) {
            throw new IllegalStateException("Release " + release.getId() + " does not match its Agent version");
        }
        var config = ReleaseAgentConfig.fromSnapshot(version.getSnapshotJson());
        if (!release.getAgentId().equals(config.getId())) {
            throw new IllegalStateException("Released snapshot Agent does not match release " + release.getId());
        }
        return new ReleasedChannelAgent(config.getId(), config.getAgentKey(), config.getName(), config);
    }
}
