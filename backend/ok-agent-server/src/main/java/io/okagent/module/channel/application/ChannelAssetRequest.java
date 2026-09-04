package io.okagent.module.channel.application;

import io.okagent.module.channel.domain.ChannelDmScope;
import io.okagent.module.channel.domain.ChannelType;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Mutable configuration of a channel instance. Secret fields are plaintext only on write; on
 * update, leaving a secret blank preserves the previously stored value.
 */
public record ChannelAssetRequest(
        @jakarta.validation.constraints.NotBlank @Size(max = 128) String name,
        ChannelType type,
        UUID boundAgentId,
        ChannelDmScope dmScope,
        FeishuConfig feishu,
        WechatConfig wechat,
        DingTalkConfig dingtalk,
        String wechatLoginId,
        String dingtalkLoginId,
        boolean enabled) {

    /** Feishu-provider non-secret config and write-only secrets. */
    public record FeishuConfig(
            @Size(max = 128) String appId,
            String appSecret,
            String encryptKey,
            String verificationToken,
            @Size(max = 512) String apiBase,
            @Size(max = 512) String callbackPath) {}

    /** WeChat iLink (ClawBot) provider config. iLink uses QR login, so there are no static secrets. */
    public record WechatConfig(
            @Size(max = 512) String apiBase,
            @Size(max = 32) String channelVersion) {}

    /** DingTalk enterprise-internal-app (Stream mode) config. appSecret is write-only. */
    public record DingTalkConfig(
            @Size(max = 128) String appKey,
            String appSecret,
            @Size(max = 128) String robotCode,
            @Size(max = 512) String apiBase,
            @Size(max = 512) String oapiBase,
            @Size(max = 512) String streamRegisterUrl) {}
}
