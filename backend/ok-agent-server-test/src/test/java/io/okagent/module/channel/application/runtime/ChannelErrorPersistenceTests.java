package io.okagent.module.channel.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.Msg;
import io.agentscope.extensions.channel.dingtalk.DingTalkChannelProperties;
import io.agentscope.harness.agent.gateway.channel.ChannelConfig;
import io.okagent.module.channel.application.ChannelIdentityResolver;
import io.okagent.module.channel.application.runtime.dingtalk.DingTalkStreamChannel;
import io.okagent.module.channel.application.runtime.feishu.FeishuWsChannel;
import io.okagent.module.channel.application.runtime.wechat.IlinkClient;
import io.okagent.module.channel.application.runtime.wechat.WeChatIlinkChannel;
import io.okagent.module.channel.infrastructure.persistence.ChannelIlinkSessionRepository;
import io.okagent.module.conversation.application.DialogueService;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

class ChannelErrorPersistenceTests {

    @Test
    void feishuPersistsStableRuntimeErrorCode() throws Exception {
        DialogueService dialogue = mock(DialogueService.class);
        FeishuWsChannel channel = new FeishuWsChannel(
                "feishu",
                ChannelConfig.of("feishu", "agent"),
                "app-id",
                "app-secret",
                mock(com.lark.oapi.Client.class),
                dialogue,
                mock(ChannelIdentityResolver.class),
                UUID.randomUUID(),
                "agent",
                "FEISHU",
                new ObjectMapper());

        invokeRecordTurnEnd(channel, "session", new RuntimeException("sk-secret-provider-body"));

        assertSanitized(dialogue);
    }

    @Test
    void dingtalkPersistsStableRuntimeErrorCode() throws Exception {
        DialogueService dialogue = mock(DialogueService.class);
        DingTalkStreamChannel channel = new DingTalkStreamChannel(
                "dingtalk",
                ChannelConfig.of("dingtalk", "agent"),
                new DingTalkChannelProperties("app-key", "app-secret", "robot", "https://api", "https://oapi", "https://stream"),
                dialogue,
                mock(ChannelIdentityResolver.class),
                UUID.randomUUID(),
                "agent",
                "DINGTALK");

        invokeRecordTurnEnd(channel, "session", new RuntimeException("sk-secret-provider-body"));

        assertSanitized(dialogue);
    }

    @Test
    void wechatPersistsStableRuntimeErrorCode() throws Exception {
        DialogueService dialogue = mock(DialogueService.class);
        WeChatIlinkChannel channel = new WeChatIlinkChannel(
                UUID.randomUUID(),
                "wechat",
                ChannelConfig.of("wechat", "agent"),
                mock(IlinkClient.class),
                "bot-token",
                mock(ChannelIlinkSessionRepository.class),
                mock(TransactionTemplate.class),
                dialogue,
                mock(ChannelIdentityResolver.class),
                UUID.randomUUID(),
                "agent");

        invokeRecordTurnEnd(channel, "session", new RuntimeException("sk-secret-provider-body"));

        assertSanitized(dialogue);
    }

    private static void invokeRecordTurnEnd(Object channel, String sessionId, Throwable failure) throws Exception {
        Method method = channel.getClass()
                .getDeclaredMethod("recordTurnEnd", String.class, Msg.class, Instant.class, Throwable.class, String.class);
        method.setAccessible(true);
        method.invoke(channel, sessionId, null, Instant.now(), failure, "trace-id");
    }

    private static void assertSanitized(DialogueService dialogue) {
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(dialogue).recordMessage(eq("session"), eq("error"), content.capture(), isNull(), anyInt(), eq("trace-id"));
        assertThat(content.getValue()).isEqualTo("Agent 执行失败：RUNTIME_UPSTREAM_FAILURE");
        assertThat(content.getValue()).doesNotContain("sk-secret-provider-body");
    }
}
