package io.okagent.module.channel.application;

import io.okagent.module.channel.application.WechatIlinkStatusResponse;
import java.util.UUID;

/**
 * Manages the WeChat iLink (ClawBot) QR-login lifecycle for a WECHAT channel: issuing a login QR
 * code, polling scan/confirmation, exposing the current login state, and clearing the session. A
 * channel can only long-poll messages after a confirmed login stores its bot_token.
 */
public interface WechatIlinkLoginService {

    /**
     * Issues a fresh login QR code for the channel, replacing any in-progress one. Returns the
     * resulting status (including the {@code qrcodeUrl} to render). Idempotent: calling again while
     * a QR is still pending returns a new QR.
     */
    WechatIlinkStatusResponse startLogin(UUID channelId);

    /**
     * Polls the pending QR scan status against iLink. On confirmation persists the encrypted
     * bot_token and triggers a runtime reconcile so the channel begins long-polling.
     */
    WechatIlinkStatusResponse pollStatus(UUID channelId);

    /** Returns the current login status without contacting iLink. */
    WechatIlinkStatusResponse getStatus(UUID channelId);

    /** Clears the stored bot_token/session and stops the channel runtime. */
    WechatIlinkStatusResponse logout(UUID channelId);
}
