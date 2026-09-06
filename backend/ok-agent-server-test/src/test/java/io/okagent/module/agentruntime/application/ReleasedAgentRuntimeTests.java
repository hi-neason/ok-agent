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
        var service = new ReleasedAgentChatService(null, resolver, channels, null, null, null, null, null, null, null);
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
}
