package io.okagent.module.channel.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.module.channel.application.runtime.dingtalk.DingTalkRegistrationService;
import io.okagent.module.channel.application.runtime.wechat.WechatLoginRegistrationService;
import io.okagent.module.model.application.ApiKeyCipher;
import java.lang.reflect.Field;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;

class RegistrationExecutorConfigurationTests {

    @Test
    void registrationPollersUseBoundedExecutors() throws Exception {
        FeishuAppRegistrationService feishu = new FeishuAppRegistrationService();
        WechatLoginRegistrationService wechat = new WechatLoginRegistrationService(new ApiKeyCipher("0123456789abcdef"));
        DingTalkRegistrationService dingtalk = new DingTalkRegistrationService(new ObjectMapper());
        try {
            assertBounded(executor(feishu));
            assertBounded(executor(wechat));
            assertBounded(executor(dingtalk));
        } finally {
            feishu.shutdown();
            wechat.shutdown();
            dingtalk.shutdown();
        }
    }

    private static void assertBounded(ThreadPoolExecutor executor) {
        assertThat(executor.getMaximumPoolSize()).isEqualTo(4);
        assertThat(executor.getQueue()).isInstanceOf(SynchronousQueue.class);
    }

    private static ThreadPoolExecutor executor(Object service) throws Exception {
        Field field = service.getClass().getDeclaredField("executor");
        field.setAccessible(true);
        return (ThreadPoolExecutor) field.get(service);
    }
}
