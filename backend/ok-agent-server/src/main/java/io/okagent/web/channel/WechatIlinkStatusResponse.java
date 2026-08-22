package io.okagent.web.channel;

import io.okagent.domain.channel.ChannelIlinkSession;
import io.okagent.domain.channel.IlinkLoginStatus;
import java.time.Instant;
import java.util.UUID;

/** Read view of a WeChat iLink channel's QR-login session. Never exposes the bot_token. */
public record WechatIlinkStatusResponse(
        UUID channelId,
        IlinkLoginStatus loginStatus,
        String qrcodeToken,
        String qrcodeUrl,
        String botId,
        String ilinkUserId,
        String lastError,
        Instant loggedInAt,
        Instant updatedAt) {

    public static WechatIlinkStatusResponse from(ChannelIlinkSession s) {
        if (s == null) {
            return null;
        }
        return new WechatIlinkStatusResponse(
                s.getChannelId(),
                s.getLoginStatus(),
                s.getQrcodeToken(),
                s.getQrcodeUrl(),
                s.getBotId(),
                s.getIlinkUserId(),
                s.getLastError(),
                s.getLoggedInAt(),
                s.getUpdatedAt());
    }
}
