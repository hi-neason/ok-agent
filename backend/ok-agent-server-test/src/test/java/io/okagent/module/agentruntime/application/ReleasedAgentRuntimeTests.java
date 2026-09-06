package io.okagent.module.agentruntime.application;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.okagent.module.channel.domain.*;
import io.okagent.module.channel.infrastructure.persistence.ChannelAssetRepository;
import io.okagent.module.release.application.*;
import io.okagent.module.customerchat.application.CustomerChatCommand;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
class ReleasedAgentRuntimeTests {
    @Test void requiresPublishedChannelAndMatchingBinding() {
        var channels = mock(ChannelAssetRepository.class);
        var resolver = mock(ReleasedChannelAgentResolver.class);
        var service = new ReleasedAgentChatService(resolver, channels, null, null, null, null, null);
        UUID agent = UUID.randomUUID(), channelId = UUID.randomUUID();
        var req = new CustomerChatCommand(agent, channelId.toString(), "s", "u", "hello");
        assertThatThrownBy(() -> service.resolveRuntime(new CustomerChatCommand(agent, "web", "s", "u", "hello")))
                .isInstanceOf(ResponseStatusException.class);
        when(channels.findById(channelId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolveRuntime(req)).isInstanceOf(ResponseStatusException.class);
        var channel = new ChannelAsset(channelId, "key", "name", ChannelType.FEISHU, agent, null, "{}", null, "{}", true, "test");
        when(channels.findById(channelId)).thenReturn(Optional.of(channel));
        when(resolver.resolve(channel)).thenThrow(new IllegalStateException("no release"));
        assertThatThrownBy(() -> service.resolveRuntime(req)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.resolveRuntime(new CustomerChatCommand(UUID.randomUUID(), channelId.toString(), "s", "u", "hello")))
                .isInstanceOf(ResponseStatusException.class);
        var config = mock(io.okagent.module.agent.application.ResolvedAgentConfig.class);
        var released = new ReleasedChannelAgent(agent, "key", "name", config, UUID.randomUUID(), 2);
        doReturn(released).when(resolver).resolve(channel);
        assertThat(service.resolveRuntime(req).config()).isSameAs(config);
        assertThat(service.resolveRuntime(req).releaseId()).isEqualTo(released.releaseId());
    }
    @Test void existingConversationKeepsItsOriginalVersionAndHistory() {
        var dialogue = mock(io.okagent.module.conversation.application.DialogueService.class);
        var versions = mock(io.okagent.module.release.infrastructure.persistence.AgentVersionRepository.class);
        var service = new ReleasedAgentChatService(null, null, null, dialogue, null, null, versions);
        var agent = UUID.randomUUID();
        var config = mock(io.okagent.module.agent.application.ResolvedAgentConfig.class);
        when(config.getId()).thenReturn(agent);
        var current = new ReleasedAgentChatService.ResolvedRuntime(config, UUID.randomUUID(), 2, true);
        var oldRelease = UUID.randomUUID();
        var session = new io.okagent.module.conversation.domain.DialogueSession("s", agent, "History", "u", java.time.Instant.now());
        session.setReleaseInfo(oldRelease, 1);
        when(dialogue.findById("s")).thenReturn(Optional.of(session));
        var version = new io.okagent.module.release.domain.AgentVersion(UUID.randomUUID(), agent, 1, null,
                "{\"agentId\":\"" + agent + "\",\"agentKey\":\"old\"}", "hash", null, null, "test");
        when(versions.findByAgentIdAndVersionNo(agent, 1)).thenReturn(Optional.of(version));
        var pinned = service.pinRuntime("s", current, "u");
        assertThat(pinned.versionNo()).isEqualTo(1);
        assertThat(pinned.releaseId()).isEqualTo(oldRelease);
        assertThat(pinned.config().getAgentKey()).isEqualTo("old");
        verify(dialogue, never()).purge(anyString());
        when(versions.findByAgentIdAndVersionNo(agent, 1)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.pinRuntime("s", current, "u")).isInstanceOf(ResponseStatusException.class);
    }
}
