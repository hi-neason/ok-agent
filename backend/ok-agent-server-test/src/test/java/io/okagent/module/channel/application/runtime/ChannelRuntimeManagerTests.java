package io.okagent.module.channel.application.runtime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.okagent.module.channel.domain.ChannelAsset;
import io.okagent.module.channel.infrastructure.persistence.ChannelAssetRepository;
import io.agentscope.harness.agent.gateway.GatewayBootstrap;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
class ChannelRuntimeManagerTests {
    @Test void aSlowChannelDoesNotBlockAnotherChannel() throws Exception {
        var repository = mock(ChannelAssetRepository.class);
        var factory = mock(ChannelGatewayFactory.class);
        var manager = new ChannelRuntimeManager(repository, factory, mock(ChannelRuntimeStatusWriter.class));
        var first = channel();
        var second = channel();
        when(repository.findById(first.getId())).thenReturn(Optional.of(first));
        when(repository.findById(second.getId())).thenReturn(Optional.of(second));
        var started = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        when(factory.build(first)).thenAnswer(invocation -> { started.countDown(); assertThat(finish.await(5, TimeUnit.SECONDS)).isTrue(); return mock(GatewayBootstrap.class); });
        var secondBootstrap = mock(GatewayBootstrap.class);
        when(factory.build(second)).thenReturn(secondBootstrap);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var slow = executor.submit(() -> manager.onChannelChanged(new ChannelRuntimeEvent(first.getId(), false)));
            assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
            executor.submit(() -> manager.onChannelChanged(new ChannelRuntimeEvent(second.getId(), false))).get(3, TimeUnit.SECONDS);
            verify(secondBootstrap).start();
            finish.countDown();
            slow.get(3, TimeUnit.SECONDS);
        } finally { finish.countDown(); executor.shutdownNow(); manager.shutdown(); }
    }
    @Test void failedStartupStopsResourcesAndNeverReportsRunning() {
        var repository = mock(ChannelAssetRepository.class);
        var factory = mock(ChannelGatewayFactory.class);
        var status = mock(ChannelRuntimeStatusWriter.class);
        var manager = new ChannelRuntimeManager(repository, factory, status);
        var asset = channel();
        when(repository.findById(asset.getId())).thenReturn(Optional.of(asset));
        var bootstrap = mock(GatewayBootstrap.class);
        when(factory.build(asset)).thenReturn(bootstrap);
        doThrow(new IllegalStateException("private provider details")).when(bootstrap).start();
        manager.onChannelChanged(new ChannelRuntimeEvent(asset.getId(), false));
        verify(bootstrap).stop();
        verify(status, never()).write(eq(asset.getId()), eq(io.okagent.module.channel.domain.ChannelRuntimeStatus.RUNNING), any());
        verify(status).write(asset.getId(), io.okagent.module.channel.domain.ChannelRuntimeStatus.ERROR, "CHANNEL_START_FAILED");
    }
    private ChannelAsset channel() {
        var channel = mock(ChannelAsset.class);
        when(channel.getId()).thenReturn(UUID.randomUUID());
        when(channel.isEnabled()).thenReturn(true);
        when(channel.getBoundAgentId()).thenReturn(UUID.randomUUID());
        return channel;
    }
}
