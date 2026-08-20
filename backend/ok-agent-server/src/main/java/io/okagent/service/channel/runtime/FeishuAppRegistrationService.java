package io.okagent.service.channel.runtime;

import com.lark.oapi.scene.registration.AppAddons;
import com.lark.oapi.scene.registration.RegisterApp;
import com.lark.oapi.scene.registration.RegisterAppException;
import com.lark.oapi.scene.registration.RegisterAppOptions;
import com.lark.oapi.scene.registration.RegisterAppResult;
import com.lark.oapi.scene.registration.StatusChangeInfo;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Drives Feishu's official "create an app in one click" flow (OAuth 2.0 device authorization,
 * RFC 8628) so a user can scan a QR code in Feishu to auto-create a self-built bot app and get
 * its App ID / Secret back without touching the developer console.
 *
 * <p>{@link RegisterApp#register(RegisterAppOptions)} blocks until the user authorizes (or
 * times out), so each flow runs on its own daemon thread and is tracked by an in-memory session.
 * The frontend starts a flow and polls {@link #status(String)}; on success the credentials are
 * returned once, then the session is consumed.
 *
 * <p>The created app is a normal self-built app that works with our long-connection (WebSocket)
 * channel. We pre-request the minimal scopes/events needed for bot messaging; more can be added
 * later through the incremental-authorization flow.
 */
@Service
public class FeishuAppRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(FeishuAppRegistrationService.class);

    // Minimal set for a bot that receives and replies to messages over the long connection.
    private static final List<String> TENANT_SCOPES =
            List.of("im:message", "im:message:send_as_bot", "im:message.p2p_msg:readonly", "im:resource");
    private static final List<String> TENANT_EVENTS = List.of("im.message.receive_v1");

    private static final long SESSION_TTL_SECONDS = 600;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "feishu-app-register");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, RegistrationSession> sessions = new ConcurrentHashMap<>();

    public StartedSession start() {
        String sessionId = UUID.randomUUID().toString();
        RegistrationSession session = new RegistrationSession(sessionId);
        sessions.put(sessionId, session);

        Future<?> future = executor.submit(() -> {
            try {
                // 不传 createOnly / appId：扫码落地页同时支持「新建应用」和「选择已有应用」，
                // 已有应用会被增量授予下方预置的 scopes/events。
                RegisterAppOptions options = RegisterAppOptions.newBuilder()
                        .source("ok-agent")
                        .addons(AppAddons.newBuilder()
                                .tenantScopes(TENANT_SCOPES)
                                .tenantEvents(TENANT_EVENTS)
                                .build())
                        .onQRCode(info -> {
                            session.qrUrl = info.getUrl();
                            session.expireAt = Instant.now().plusSeconds(Math.max(1, info.getExpireIn()));
                            session.state = State.WAITING_SCAN;
                            log.info(
                                    "Feishu app-registration '{}': QR ready (expires in {}s)",
                                    sessionId,
                                    info.getExpireIn());
                        })
                        .onStatusChange(this::onStatus)
                        .build();

                RegisterAppResult result = RegisterApp.register(options);
                session.appId = result.getClientId();
                session.appSecret = result.getClientSecret();
                session.state = State.SUCCESS;
                log.info(
                        "Feishu app-registration '{}': authorized (appId={}, secretReturned={})",
                        sessionId,
                        result.getClientId(),
                        result.getClientSecret() != null && !result.getClientSecret().isBlank());
            } catch (RegisterAppException e) {
                session.state = State.FAILED;
                session.error = e.getCode() != null ? e.getCode() + " " + e.getDescription() : e.getMessage();
                log.warn("Feishu app-registration '{}' failed: {}", sessionId, session.error);
            } catch (Exception e) {
                session.state = State.FAILED;
                session.error = e.getMessage();
                log.warn("Feishu app-registration '{}' failed", sessionId, e);
            }
        });
        session.future = future;
        return new StartedSession(sessionId);
    }

    private void onStatus(StatusChangeInfo info) {
        log.debug(
                "Feishu app-registration status: {}{}",
                info.getStatus(),
                info.getInterval() > 0 ? " (interval " + info.getInterval() + "s)" : "");
    }

    public SessionStatus status(String sessionId) {
        RegistrationSession s = sessions.get(sessionId);
        if (s == null) {
            return new SessionStatus("NOT_FOUND", null, null, null, 0, null);
        }
        if (s.expireAt != null && s.state != State.SUCCESS && Instant.now().isAfter(s.expireAt.plusSeconds(60))) {
            s.state = State.EXPIRED;
        }
        return new SessionStatus(
                s.state.name(),
                s.qrUrl,
                s.appId,
                s.appSecret,
                s.expireAt != null ? s.expireAt.getEpochSecond() : 0,
                s.error);
    }

    @PreDestroy
    public void shutdown() {
        sessions.values().forEach(s -> s.future.cancel(true));
        executor.shutdownNow();
    }

    private enum State {
        STARTING,
        WAITING_SCAN,
        SUCCESS,
        FAILED,
        EXPIRED
    }

    private static final class RegistrationSession {
        final String id;
        volatile State state = State.STARTING;
        volatile String qrUrl;
        volatile Instant expireAt;
        volatile String appId;
        volatile String appSecret;
        volatile String error;
        volatile Future<?> future;

        RegistrationSession(String id) {
            this.id = id;
        }
    }

    /** Returned immediately when a flow starts; the frontend polls with the session id. */
    public record StartedSession(String sessionId) {}

    /** Polling view of a registration flow. Credentials are only present on SUCCESS. */
    public record SessionStatus(
            String state, String qrUrl, String appId, String appSecret, long expireAt, String error) {}
}
