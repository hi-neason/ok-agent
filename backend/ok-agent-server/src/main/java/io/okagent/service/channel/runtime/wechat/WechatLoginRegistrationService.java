package io.okagent.service.channel.runtime.wechat;

import io.okagent.domain.channel.ChannelIlinkSession;
import io.okagent.service.model.ApiKeyCipher;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Drives the WeChat iLink (ClawBot) QR-login flow independently of any channel, mirroring
 * {@link io.okagent.service.channel.runtime.FeishuAppRegistrationService}.
 *
 * <p>The "create channel" dialog starts a flow with {@link #start(StartRequest)}, gets back a
 * {@code loginId}, polls {@link #status(String)} while the user scans/confirms on the phone, and on
 * success the (encrypted) bot_token is held in an in-memory session. When the user saves the channel,
 * {@link ChannelAssetService} calls {@link #consume(String)} to claim the credentials and persist
 * them onto a fresh {@link ChannelIlinkSession} in one transaction. No channel row exists before
 * save, so there is nothing to delete on cancel and no concurrent-delete races.
 *
 * <p>Sessions live in memory (same trade-off as the Feishu flow): a server restart drops pending
 * scans, which is acceptable for an interactive create flow. Each flow runs its own daemon polling
 * thread and self-expires after {@link #SESSION_TTL}.
 */
@Service
public class WechatLoginRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(WechatLoginRegistrationService.class);

    private static final long SESSION_TTL_SECONDS = 480; // 8 min, matching the reference SDK QR timeout
    private static final long POLL_INTERVAL_MS = 2000;

    private final ApiKeyCipher cipher;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "wechat-ilink-register");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, RegistrationSession> sessions = new ConcurrentHashMap<>();

    public WechatLoginRegistrationService(ApiKeyCipher cipher) {
        this.cipher = cipher;
    }

    public StartedSession start(StartRequest request) {
        String loginId = UUID.randomUUID().toString();
        RegistrationSession session = new RegistrationSession(loginId, request);
        sessions.put(loginId, session);

        Future<?> future = executor.submit(() -> run(session));
        session.future = future;
        return new StartedSession(loginId);
    }

    public SessionStatus status(String loginId) {
        RegistrationSession s = sessions.get(loginId);
        if (s == null) {
            return new SessionStatus("NOT_FOUND", null, null, null, null, 0, null);
        }
        if (s.state != State.SUCCESS
                && s.expireAt != null
                && Instant.now().isAfter(s.expireAt)) {
            s.state = State.EXPIRED;
        }
        return new SessionStatus(
                s.state.name(),
                s.qrcodePayload,
                s.botId,
                s.ilinkUserId,
                s.error,
                s.expireAt != null ? s.expireAt.getEpochSecond() : 0,
                s.loginId);
    }

    /**
     * Atomically claims the credentials of a confirmed login and removes the session. Returns null
     * if the loginId is unknown, not yet confirmed, or already consumed. Called within the channel
     * creation transaction.
     */
    public ClaimedCredentials consume(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return null;
        }
        RegistrationSession s = sessions.get(loginId);
        if (s == null || s.state != State.SUCCESS || s.botTokenCiphertext == null) {
            return null;
        }
        // remove so the same login can't bind two channels
        if (sessions.remove(loginId, s)) {
            if (s.future != null) {
                s.future.cancel(true);
            }
            return new ClaimedCredentials(
                    s.botTokenCiphertext, s.botId, s.ilinkUserId, s.request.apiBase(), s.request.channelVersion());
        }
        return null;
    }

    private void run(RegistrationSession s) {
        IlinkClient client = new IlinkClient(s.request.apiBase(), s.request.channelVersion());
        try {
            IlinkClient.QrSession qr = client.requestQrCode();
            s.qrcodeToken = qr.qrcodeToken();
            s.qrcodePayload = qr.qrcodeImgContent();
            s.expireAt = Instant.now().plusSeconds(SESSION_TTL_SECONDS);
            s.state = State.WAITING_SCAN;

            long deadline = System.currentTimeMillis() + SESSION_TTL_SECONDS * 1000L;
            while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
                IlinkClient.QrStatus st = client.pollQrStatus(s.qrcodeToken);
                if (st == null) {
                    Thread.sleep(POLL_INTERVAL_MS);
                    continue;
                }
                if (st.confirmed() && st.botToken() != null) {
                    s.botTokenCiphertext = cipher.encrypt(st.botToken());
                    s.botId = st.botId();
                    s.ilinkUserId = st.ilinkUserId();
                    s.state = State.SUCCESS;
                    log.info("WeChat iLink registration '{}': confirmed (botId={})", s.loginId, s.botId);
                    return;
                }
                if (st.scanned()) {
                    s.state = State.SCANNED;
                } else if (st.expired()) {
                    s.state = State.EXPIRED;
                    s.error = "二维码已过期";
                    return;
                } else {
                    s.state = State.WAITING_SCAN;
                }
                Thread.sleep(POLL_INTERVAL_MS);
            }
            s.state = State.EXPIRED;
            s.error = "扫码超时，请重试";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            s.state = State.FAILED;
            s.error = e.getMessage();
            log.warn("WeChat iLink registration '{}' failed", s.loginId, e);
        }
    }

    @PreDestroy
    public void shutdown() {
        sessions.values().forEach(s -> {
            if (s.future != null) {
                s.future.cancel(true);
            }
        });
        sessions.clear();
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------
    //  Types
    // ------------------------------------------------------------------

    /** Per-dialog start params. apiBase/channelVersion come from the form. */
    public record StartRequest(String apiBase, String channelVersion) {
        public StartRequest {
            if (apiBase == null || apiBase.isBlank()) {
                apiBase = "https://ilinkai.weixin.qq.com";
            }
            if (channelVersion == null || channelVersion.isBlank()) {
                channelVersion = "0.1.0";
            }
        }
    }

    public record StartedSession(String loginId) {}

    public record SessionStatus(
            String state,
            String qrcodePayload,
            String botId,
            String ilinkUserId,
            String error,
            long expireAt,
            String loginId) {}

    /** Credentials claimed once and handed to the channel-creation transaction. */
    public record ClaimedCredentials(
            String botTokenCiphertext,
            String botId,
            String ilinkUserId,
            String apiBase,
            String channelVersion) {}

    private enum State {
        STARTING,
        WAITING_SCAN,
        SCANNED,
        SUCCESS,
        FAILED,
        EXPIRED
    }

    private static final class RegistrationSession {
        final String loginId;
        final StartRequest request;
        final AtomicBoolean consumed = new AtomicBoolean(false);
        volatile State state = State.STARTING;
        volatile Future<?> future;
        volatile String qrcodeToken;
        volatile String qrcodePayload;
        volatile Instant expireAt;
        volatile String botTokenCiphertext;
        volatile String botId;
        volatile String ilinkUserId;
        volatile String error;

        RegistrationSession(String loginId, StartRequest request) {
            this.loginId = loginId;
            this.request = request;
        }
    }
}
