package io.okagent.module.agentmanager.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.okagent.module.agent.domain.AgentAsset;
import io.okagent.module.channel.domain.ChannelAsset;
import io.okagent.module.channel.domain.ChannelType;
import io.okagent.module.release.domain.AgentRelease;
import io.okagent.module.release.domain.AgentVersion;
import io.okagent.module.release.domain.ReleaseStatus;
import io.okagent.module.release.domain.ReleaseTargetType;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.channel.infrastructure.persistence.ChannelAssetRepository;
import io.okagent.module.release.infrastructure.persistence.AgentReleaseRepository;
import io.okagent.module.release.infrastructure.persistence.AgentVersionRepository;
import io.okagent.module.release.application.AgentSnapshotService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ReleaseServiceImplTests {

    private AgentAssetRepository agents;
    private AgentVersionRepository versions;
    private AgentReleaseRepository releases;
    private ChannelAssetRepository channels;
    private AgentSnapshotService snapshots;
    private ReleaseServiceImpl service;
    private ApplicationEventPublisher events;

    private final UUID agentId = UUID.randomUUID();
    private final UUID channelId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        agents = mock(AgentAssetRepository.class);
        versions = mock(AgentVersionRepository.class);
        releases = mock(AgentReleaseRepository.class);
        channels = mock(ChannelAssetRepository.class);
        snapshots = mock(AgentSnapshotService.class);
        events = mock(ApplicationEventPublisher.class);
        service = new ReleaseServiceImpl(agents, versions, releases, channels, snapshots, events);
        when(agents.findById(agentId)).thenReturn(Optional.of(mock(AgentAsset.class)));
        when(snapshots.buildSnapshot(any()))
                .thenReturn(new AgentSnapshotService.SnapshotBundle("{\"id\":1}", "a".repeat(64), java.util.List.of()));
        when(versions.save(any(AgentVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(releases.save(any(AgentRelease.class))).thenAnswer(inv -> inv.getArgument(0));
        when(channels.save(any(ChannelAsset.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ChannelAsset channel(UUID boundAgentId) {
        return new ChannelAsset(
                channelId,
                "wechat-demo",
                "演示渠道",
                ChannelType.WECHAT,
                boundAgentId,
                null,
                "{}",
                null,
                "{}",
                true,
                "tester");
    }

    private AgentVersion version(UUID id, int no) {
        return new AgentVersion(
                id, agentId, no, null, "{\"v\":" + no + "}", "b".repeat(64), null, "release v" + no, "tester");
    }

    @Test
    void createVersionAllocatesSequentialNumbersAndLinksParent() {
        when(versions.findTopByAgentIdOrderByVersionNoDesc(agentId)).thenReturn(Optional.empty());
        AgentVersion v1 = service.createVersion(agentId, "v1.0", "first", "tester");
        assertThat(v1.getVersionNo()).isEqualTo(1);
        assertThat(v1.getParentVersionId()).isNull();
        assertThat(v1.getVersionLabel()).isEqualTo("v1.0");

        UUID v1Id = v1.getId();
        when(versions.findTopByAgentIdOrderByVersionNoDesc(agentId))
                .thenReturn(Optional.of(
                        new AgentVersion(v1Id, agentId, 1, null, "{}", "c".repeat(64), null, null, "tester")));
        AgentVersion v2 = service.createVersion(agentId, null, "second", "tester");
        assertThat(v2.getVersionNo()).isEqualTo(2);
        assertThat(v2.getParentVersionId()).isEqualTo(v1Id);
        assertThat(v2.getVersionLabel()).isNull();
    }

    @Test
    void publishRejectsChannelBoundToAnotherAgent() {
        UUID otherAgent = UUID.randomUUID();
        when(channels.findById(channelId)).thenReturn(Optional.of(channel(otherAgent)));
        assertThatThrownBy(() -> service.publishToChannel(agentId, 1, channelId, "tester"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(releases, never()).save(any(AgentRelease.class));
    }

    @Test
    void publishSupersedesCurrentReleaseAndMovesChannelPointer() {
        ChannelAsset ch = channel(agentId);
        UUID v1Id = UUID.randomUUID();
        ch.promoteRelease(v1Id); // simulate an existing published release (id = v1Id used as release id)
        when(channels.findById(channelId)).thenReturn(Optional.of(ch));
        AgentVersion v2 = version(UUID.randomUUID(), 2);
        when(versions.findByAgentIdAndVersionNo(agentId, 2)).thenReturn(Optional.of(v2));

        AgentRelease current = new AgentRelease(
                v1Id, agentId, UUID.randomUUID(), 1, ReleaseTargetType.CHANNEL, channelId, null, "tester");
        when(releases.findByTargetTypeAndTargetIdAndStatus(
                        ReleaseTargetType.CHANNEL, channelId, ReleaseStatus.PROMOTED))
                .thenReturn(Optional.of(current));

        AgentRelease published = service.publishToChannel(agentId, 2, channelId, "tester");

        assertThat(published.getStatus()).isEqualTo(ReleaseStatus.PROMOTED);
        assertThat(published.getVersionNo()).isEqualTo(2);
        assertThat(current.getStatus()).isEqualTo(ReleaseStatus.SUPERSEDED);
        assertThat(ch.getCurrentReleaseId()).isEqualTo(published.getId());
        assertThat(ch.getPreviousReleaseId()).isEqualTo(v1Id);
        verify(events).publishEvent(new io.okagent.module.channel.application.runtime.ChannelRuntimeEvent(channelId, false));
    }

    @Test
    void rollbackRePromotesPreviousAndMarksCurrentRolledBack() {
        ChannelAsset ch = channel(agentId);
        UUID currentReleaseId = UUID.randomUUID();
        UUID previousReleaseId = UUID.randomUUID();
        ch.setReleasePointers(currentReleaseId, previousReleaseId);
        when(channels.findById(channelId)).thenReturn(Optional.of(ch));

        AgentRelease previous = new AgentRelease(
                previousReleaseId, agentId, UUID.randomUUID(), 1, ReleaseTargetType.CHANNEL, channelId, null, "tester");
        when(releases.findById(previousReleaseId)).thenReturn(Optional.of(previous));
        AgentRelease current = new AgentRelease(
                currentReleaseId, agentId, UUID.randomUUID(), 2, ReleaseTargetType.CHANNEL, channelId, null, "tester");
        when(releases.findByTargetTypeAndTargetIdAndStatus(
                        ReleaseTargetType.CHANNEL, channelId, ReleaseStatus.PROMOTED))
                .thenReturn(Optional.of(current));

        AgentRelease restored = service.rollbackChannel(channelId, "tester");

        assertThat(restored.getStatus()).isEqualTo(ReleaseStatus.PROMOTED);
        assertThat(restored.getVersionNo()).isEqualTo(1);
        assertThat(restored.getRollbackOfId()).isEqualTo(previousReleaseId);
        assertThat(current.getStatus()).isEqualTo(ReleaseStatus.ROLLED_BACK);
        assertThat(ch.getCurrentReleaseId()).isEqualTo(restored.getId());
        // remember what we rolled back from, enabling a second rollback to toggle forward
        assertThat(ch.getPreviousReleaseId()).isEqualTo(currentReleaseId);
        verify(events).publishEvent(new io.okagent.module.channel.application.runtime.ChannelRuntimeEvent(channelId, false));
    }

    @Test
    void rollbackRejectsWhenNoPreviousVersion() {
        ChannelAsset ch = channel(agentId);
        when(channels.findById(channelId)).thenReturn(Optional.of(ch));
        assertThatThrownBy(() -> service.rollbackChannel(channelId, "tester"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
