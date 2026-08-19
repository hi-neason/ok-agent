package io.okagent.web.channel;

import io.okagent.domain.channel.ChannelDmScope;
import io.okagent.domain.channel.ChannelType;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Mutable configuration of a channel instance. Secret fields are plaintext only on write; on
 * update, leaving a secret blank preserves the previously stored value.
 */
public record ChannelAssetRequest(
        @Size(max = 128) String name,
        ChannelType type,
        UUID boundAgentId,
        ChannelDmScope dmScope,
        FeishuConfig feishu,
        boolean enabled) {

    /** Feishu-provider non-secret config and write-only secrets. */
    public record FeishuConfig(
            @Size(max = 128) String appId,
            String appSecret,
            String encryptKey,
            String verificationToken,
            @Size(max = 512) String apiBase,
            @Size(max = 512) String callbackPath) {}
}
