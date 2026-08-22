package io.okagent.service.channel.runtime.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
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
 * Drives the DingTalk "scan QR to create/bind a robot" device-authorization flow independently of
 * any channel row, mirroring {@link io.okagent.service.channel.runtime.wechat.WechatLoginRegistrationService}.
 *
 * <p>The protocol is the OAuth 2.0 Device Authorization Grant against DingTalk's registration
 * endpoint ({@code https://oapi.dingtalk.com}):
 * <ol>
 *   <li>{@code POST /app/registration/init} {@code {source}} &rarr; {@code {nonce}}</li>
 *   <li>{@code POST /app/registration/begin} {@code {nonce}} &rarr;
 *       {@code {device_code, verification_uri_complete, expires_in, interval}}</li>
 *   <li>{@code POST /app/registration/poll} {@code {device_code}} &rarr;
 *       {@code {status: WAITING|SUCCESS|FAIL|EXPIRED}}; on SUCCESS it carries
 *       {@code client_id} (AppKey) and {@code client_secret} (AppSecret).</li>
 * </ol>
 *
 * <p>{@link #start()} performs the init+begin calls synchronously and returns the
 * {@code verification_uri_complete} URL which the frontend renders as a QR code. A daemon thread
 * then polls by the server-advertised {@code interval} until the user scans/confirms, the code
 * expires, or the session TTL elapses. On success the AppKey/AppSecret are held in memory; when the
 * user saves the channel, {@link #consume(String)} atomically claims them and the channel-creation
 * transaction persists {@code appKey = client_id}, {@code robotCode = client_id},
 * {@code appSecret = client_secret} (encrypted at the service layer).
 *
 * <p>Reference: the {@code dsh-im} project's {@code device-auth.mjs} / {@code dingtalk-controller.mjs}.
 * The DingTalk Stream client only needs AppKey + AppSecret; the outbound {@code robotCode} is the
 * AppKey itself, so the scan credentials alone are sufficient to drive the runtime — no extra API
 * call is needed.
 */
@Service
public class DingTalkRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(DingTalkRegistrationService.class);

    private static final String REGISTRATION_BASE = "https://oapi.dingtalk.com";
    private static final String SOURCE = "DING_DWS_CLAW";
    private static final long HTTP_TIMEOUT_SECONDS = 15;
    private static final long FALLBACK_INTERVAL_SECONDS = 5;
    private static final long SESSION_TTL_SECONDS = 7200; // the device code advertises ~2h

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
            .build();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "dingtalk-register");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, RegistrationSession> sessions = new ConcurrentHashMap<>();

    public StartedSession start() {
        String loginId = UUID.randomUUID().toString();
        RegistrationSession session = new RegistrationSession(loginId);
        sessions.put(loginId, session);
        try {
            // init -> nonce
            JsonNode init = post("/app/registration/init", Map.of("source", SOURCE));
            String nonce = text(init, "nonce");
            if (nonce == null) {
                throw new IllegalStateException("钉钉扫码初始化缺少 nonce");
            }
            // begin -> device_code + verification URL
            JsonNode begun = post("/app/registration/begin", Map.of("nonce", nonce));
            String deviceCode = text(begun, "device_code");
            String verificationUrl = text(begun, "verification_uri_complete");
            if (deviceCode == null || verificationUrl == null) {
                throw new IllegalStateException("钉钉扫码服务返回的信息不完整");
            }
            session.deviceCode = deviceCode;
            session.verificationUrl = verificationUrl;
            session.userCode = text(begun, "user_code");
            session.expireAt = Instant.now().plusSeconds(SESSION_TTL_SECONDS);
            long interval = longPositive(begun, "interval", FALLBACK_INTERVAL_SECONDS);
            session.intervalSeconds = interval;
            session.state = State.WAITING_SCAN;

            Future<?> future = executor.submit(() -> run(session));
            session.future = future;
            return new StartedSession(loginId, verificationUrl, session.userCode,
                    session.expireAt.getEpochSecond(), interval);
        } catch (Exception e) {
            session.state = State.FAILED;
            session.error = e.getMessage();
            sessions.remove(loginId);
            log.warn("DingTalk registration '{}' failed to start", loginId, e);
            throw new IllegalStateException("无法生成钉钉二维码：" + e.getMessage(), e);
        }
    }

    public SessionStatus status(String loginId) {
        RegistrationSession s = sessions.get(loginId);
        if (s == null) {
            return new SessionStatus("NOT_FOUND", null, null, null, 0, 0, null);
        }
        if (s.state != State.SUCCESS && s.expireAt != null && Instant.now().isAfter(s.expireAt)) {
            s.state = State.EXPIRED;
            s.error = "二维码已过期，请重新生成";
        }
        return new SessionStatus(
                s.state.name(),
                s.verificationUrl,
                s.appKey,
                s.error,
                s.expireAt != null ? s.expireAt.getEpochSecond() : 0,
                s.intervalSeconds,
                s.loginId);
    }

    /**
     * Atomically claims the credentials of a confirmed scan and removes the session. Returns null
     * if the loginId is unknown, not yet confirmed, or already consumed. Called within the channel
     * creation transaction.
     */
    public ClaimedCredentials consume(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return null;
        }
        RegistrationSession s = sessions.get(loginId);
        if (s == null || s.state != State.SUCCESS || s.appSecret == null) {
            return null;
        }
        if (sessions.remove(loginId, s)) {
            if (s.future != null) {
                s.future.cancel(true);
            }
            return new ClaimedCredentials(s.appKey, s.appSecret);
        }
        return null;
    }

    private void run(RegistrationSession s) {
        long deadline = System.currentTimeMillis() + SESSION_TTL_SECONDS * 1000L;
        try {
            while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
                Thread.sleep(s.intervalSeconds * 1000L);
                JsonNode polled = post("/app/registration/poll", Map.of("device_code", s.deviceCode));
                String st = text(polled, "status");
                if (st == null) {
                    continue;
                }
                switch (st.toUpperCase()) {
                    case "SUCCESS" -> {
                        String clientId = text(polled, "client_id");
                        String clientSecret = text(polled, "client_secret");
                        if (clientId == null || clientSecret == null) {
                            s.state = State.FAILED;
                            s.error = "钉钉已授权，但没有返回机器人凭据";
                            return;
                        }
                        s.appKey = clientId;
                        s.appSecret = clientSecret;
                        s.state = State.SUCCESS;
                        log.info("DingTalk registration '{}': confirmed (appKey={})", s.loginId, clientId);
                        return;
                    }
                    case "EXPIRED" -> {
                        s.state = State.EXPIRED;
                        s.error = "二维码已过期，请重新生成";
                        return;
                    }
                    case "FAIL" -> {
                        s.state = State.FAILED;
                        s.error = text(polled, "fail_reason");
                        if (s.error == null) {
                            s.error = "钉钉未完成机器人授权，请重新扫码";
                        }
                        return;
                    }
                    case "WAITING", "UNKNOWN" -> {
                        s.state = State.WAITING_SCAN;
                    }
                    default -> {
                        // keep waiting
                    }
                }
            }
            s.state = State.EXPIRED;
            s.error = "扫码超时，请重试";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            s.state = State.FAILED;
            s.error = e.getMessage();
            log.warn("DingTalk registration '{}' failed", s.loginId, e);
        }
    }

    // ------------------------------------------------------------------
    //  HTTP helpers
    // ------------------------------------------------------------------

    private JsonNode post(String path, Map<String, ?> body) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(REGISTRATION_BASE + path))
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("钉钉服务请求失败（HTTP " + response.statusCode() + "）");
        }
        JsonNode node = json.readTree(response.body());
        if (node == null || node.path("errcode").asInt(-1) != 0) {
            String msg = node == null ? null : text(node, "errmsg");
            throw new IllegalStateException("钉钉扫码请求被拒绝：" + (msg == null ? "未知错误" : msg));
        }
        return node;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static long longPositive(JsonNode node, String field, long fallback) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return fallback;
        }
        long n = v.asLong(fallback);
        return n > 0 ? n : fallback;
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

    public record StartedSession(
            String loginId,
            String verificationUrl,
            String userCode,
            long expireAt,
            long intervalSeconds) {}

    public record SessionStatus(
            String state,
            String verificationUrl,
            String appKey,
            String error,
            long expireAt,
            long intervalSeconds,
            String loginId) {}

    /** Credentials claimed once and handed to the channel-creation transaction. */
    public record ClaimedCredentials(String appKey, String appSecret) {}

    private enum State {
        STARTING,
        WAITING_SCAN,
        SUCCESS,
        FAILED,
        EXPIRED
    }

    private static final class RegistrationSession {
        final String loginId;
        volatile Future<?> future;
        volatile String deviceCode;
        volatile String verificationUrl;
        volatile String userCode;
        volatile Instant expireAt;
        volatile long intervalSeconds = FALLBACK_INTERVAL_SECONDS;
        volatile State state = State.STARTING;
        volatile String appKey;
        volatile String appSecret;
        volatile String error;

        RegistrationSession(String loginId) {
            this.loginId = loginId;
        }
    }
}
