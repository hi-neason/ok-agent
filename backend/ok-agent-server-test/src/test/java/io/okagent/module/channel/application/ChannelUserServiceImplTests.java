package io.okagent.module.channel.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.okagent.module.channel.infrastructure.persistence.ChannelUserIdentityRepository;
import org.junit.jupiter.api.Test;

class ChannelUserServiceImplTests {

    @Test
    void ignoresBestEffortTrackingAfterShutdown() {
        var repository = mock(ChannelUserIdentityRepository.class);
        var service = new ChannelUserServiceImpl(repository);
        service.shutdown();

        assertThatCode(() -> service.recordInbound("FEISHU", "channel", "external", null, null, "User", null))
                .doesNotThrowAnyException();
        verifyNoInteractions(repository);
    }
}
