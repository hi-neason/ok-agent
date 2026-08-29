package io.okagent.module.release.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.okagent.module.channel.domain.ChannelAsset;
import io.okagent.module.channel.domain.ChannelType;
import io.okagent.module.release.domain.AgentRelease;
import io.okagent.module.release.domain.AgentVersion;
import io.okagent.module.release.domain.ReleaseTargetType;
import io.okagent.module.release.infrastructure.persistence.AgentReleaseRepository;
import io.okagent.module.release.infrastructure.persistence.AgentVersionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReleasedChannelAgentResolverImplTests {

    private AgentReleaseRepository releases;
    private AgentVersionRepository versions;
    private ReleasedChannelAgentResolverImpl resolver;

    @BeforeEach
    void setUp() {
        releases = mock(AgentReleaseRepository.class);
        versions = mock(AgentVersionRepository.class);
        resolver = new ReleasedChannelAgentResolverImpl(releases, versions);
    }

    @Test
    void resolvesPromotedSnapshotWithoutReadingDraft() {
        UUID agentId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID releaseId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        ChannelAsset channel = channel(channelId, agentId);
        channel.promoteRelease(releaseId);
        AgentRelease release = new AgentRelease(
                releaseId, agentId, versionId, 3, ReleaseTargetType.CHANNEL, channelId, null, "tester");
        String snapshot =
                "{\"agentId\":\"" + agentId + "\",\"agentKey\":\"support\",\"name\":\"Support\",\"subagents\":[]}";
        AgentVersion version =
                new AgentVersion(versionId, agentId, 3, null, snapshot, "a".repeat(64), null, null, "tester");
        when(releases.findById(releaseId)).thenReturn(Optional.of(release));
        when(versions.findById(versionId)).thenReturn(Optional.of(version));

        ReleasedChannelAgent resolved = resolver.resolve(channel);

        assertThat(resolved.agentId()).isEqualTo(agentId);
        assertThat(resolved.agentKey()).isEqualTo("support");
        assertThat(resolved.agentName()).isEqualTo("Support");
        assertThat(resolved.config()).isInstanceOf(ReleaseAgentConfig.class);
    }

    @Test
    void rejectsEnabledChannelWithoutPromotedRelease() {
        ChannelAsset channel = channel(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> resolver.resolve(channel))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no promoted release");
    }

    private ChannelAsset channel(UUID channelId, UUID agentId) {
        return new ChannelAsset(
                channelId,
                "channel-key",
                "Channel",
                ChannelType.FEISHU,
                agentId,
                null,
                "{}",
                null,
                "{}",
                true,
                "tester");
    }
}
