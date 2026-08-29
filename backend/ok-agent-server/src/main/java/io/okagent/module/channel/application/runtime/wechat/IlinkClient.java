package io.okagent.module.channel.application.runtime.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Low-level HTTP client for the WeChat iLink (ClawBot) protocol.
 *
 * <p>iLink is a pure HTTP/JSON, long-polling protocol: QR login → long-poll {@code getupdates} →
 * reply with {@code sendmessage} carrying the inbound {@code context_token}. This client owns no
 * business logic; it only marshals requests/responses and the required auth headers.
 *
 * <p>Field names, headers, enum values and timeouts follow the reference implementation
 * ({@code wechat-ilink-bot-sdk}, protocol version {@code 0.1.0}, bot_type {@code 3}).
 * Media (CDN + AES) is not implemented yet — only text messages are exchanged.
 */
public class IlinkClient {

    private static final Logger log = LoggerFactory.getLogger(IlinkClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String apiBase;
    private final String channelVersion;
    private final HttpClient http;

    public IlinkClient(String apiBase, String channelVersion) {
        this.apiBase = normalizeBase(apiBase);
        this.channelVersion = channelVersion == null || channelVersion.isBlank() ? "0.1.0" : channelVersion;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Result of the QR-code login request.
     *
     * @param qrcodeToken       the qrcode identifier used to poll status
     * @param qrcodeImgContent  the {@code qrcode_img_content} field — an image URL / data URI that
     *                          can be displayed directly, or the raw token to render as a QR image
     */
    public record QrSession(String qrcodeToken, String qrcodeImgContent) {}

    /** QR poll outcome. {@code status} is one of wait/scaned/confirmed/expired. */
    public record QrStatus(String status, String botToken, String botId, String ilinkUserId) {
        public boolean confirmed() {
            return "confirmed".equalsIgnoreCase(status);
        }

        public boolean expired() {
            return "expired".equalsIgnoreCase(status);
        }

        public boolean scanned() {
            return "scaned".equalsIgnoreCase(status) || "scanned".equalsIgnoreCase(status);
        }
    }

    /** One inbound text message extracted from a getupdates response. */
    public record IncomingMessage(
            String messageId,
            String fromUserId,
            String toUserId,
            String contextToken,
            Long createTimeMs,
            String text) {}

    /** A long-poll result: the cursor to use next time plus the messages received. */
    public record UpdateBatch(String nextCursor, List<IncomingMessage> messages) {}

    // -----------------------------------------------------------------
    //  Login
    // -----------------------------------------------------------------

    /** Requests a login QR code. */
    public QrSession requestQrCode() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/ilink/bot/get_bot_qrcode?bot_type=3"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        JsonNode root = sendAndParse(req);
        String token = text(root, "qrcode", "qrcode_token", "qrcodeToken");
        String imgContent = text(root, "qrcode_img_content", "qrcodeImgContent", "qrcode_url", "qrcodeUrl");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("iLink QR response missing qrcode token: " + root);
        }
        return new QrSession(token, imgContent);
    }

    /**
     * Polls the QR scan status. The status endpoint requires the {@code iLink-App-ClientVersion: 1}
     * header and long-polls up to ~35s.
     */
    public QrStatus pollQrStatus(String qrcodeToken) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/ilink/bot/get_qrcode_status?qrcode=" + qrcodeToken))
                .timeout(Duration.ofSeconds(40))
                .header("iLink-App-ClientVersion", "1")
                .GET();
        JsonNode root = sendAndParse(b.build());
        String status = text(root, "status");
        // On "confirmed" the credentials are returned at the top level of the status payload.
        String botToken = text(root, "bot_token", "botToken");
        String botId = text(root, "ilink_bot_id", "bot_id", "botId");
        String ilinkUserId = text(root, "ilink_user_id", "ilinkUserId", "user_id");
        return new QrStatus(status, botToken, botId, ilinkUserId);
    }

    // -----------------------------------------------------------------
    //  Long polling inbound
    // -----------------------------------------------------------------

    /**
     * Long-polls for new messages. The server holds the connection up to ~35s. Returns the
     * (possibly advanced) cursor and any messages. Non-text items are ignored.
     */
    public UpdateBatch getUpdates(String botToken, String cursor) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("get_updates_buf", cursor == null ? "" : cursor);
        body.put("base_info", Map.of("channel_version", channelVersion));
        HttpRequest req = newRequest(apiBase + "/ilink/bot/getupdates", botToken)
                .timeout(Duration.ofSeconds(40))
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        JsonNode root = sendAndParse(req);
        String nextCursor = text(root, "get_updates_buf", "next_cursor", "cursor", "new_cursor");
        if (nextCursor == null) {
            nextCursor = cursor == null ? "" : cursor;
        }
        JsonNode arr = root.path("msgs");
        if (!arr.isArray()) {
            return new UpdateBatch(nextCursor, List.of());
        }
        List<IncomingMessage> messages = new java.util.ArrayList<>();
        for (JsonNode msg : arr) {
            IncomingMessage parsed = parseMessage(msg);
            if (parsed != null) {
                messages.add(parsed);
            }
        }
        return new UpdateBatch(nextCursor, messages);
    }

    private IncomingMessage parseMessage(JsonNode msg) {
        // Only process user messages (message_type=1 / USER). Skip bot/system echo messages so the
        // bot never reacts to its own replies. If the field is absent we tolerate it and process.
        JsonNode typeNode = msg.get("message_type");
        if (typeNode != null && typeNode.isNumber() && typeNode.asInt() != 1) {
            return null;
        }
        String from = text(msg, "from_user_id", "fromUserId");
        String to = text(msg, "to_user_id", "toUserId");
        String context = text(msg, "context_token", "contextToken");
        if (from == null || from.isBlank()) {
            return null;
        }
        String text = null;
        JsonNode items = msg.path("item_list");
        if (items.isArray()) {
            for (JsonNode item : items) {
                int type = item.path("type").asInt(0);
                JsonNode textItem = item.path("text_item");
                if (type == 1 && !textItem.isMissingNode() && !textItem.isNull()) {
                    text = textItem.path("text").asText(null);
                    if (text != null && !text.isBlank()) {
                        break;
                    }
                }
            }
        }
        if (text == null || text.isBlank()) {
            return null;
        }
        return new IncomingMessage(
                text(msg, "client_id", "message_id", "msg_id", "msgId"),
                from,
                to,
                context,
                msg.path("create_time_ms").isNumber() ? msg.path("create_time_ms").asLong() : null,
                text.trim());
    }

    // -----------------------------------------------------------------
    //  Outbound
    // -----------------------------------------------------------------

    /** Replies with a text message, carrying the inbound context_token. */
    public void sendText(String botToken, String toUserId, String contextToken, String text) throws Exception {
        if (toUserId == null || toUserId.isBlank() || text == null || text.isBlank()) {
            return;
        }
        // message_type=2 (BOT) / message_state=2 (FINISH); item type 1 = text. from_user_id is empty
        // for bot outbound; client_id is a per-message de-dup id; context_token routes the reply back
        // to the originating chat. base_info.channel_version is required on send too.
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("from_user_id", "");
        msg.put("to_user_id", toUserId);
        msg.put("client_id", generateClientId());
        msg.put("message_type", 2);
        msg.put("message_state", 2);
        msg.put("context_token", contextToken == null ? "" : contextToken);
        msg.put("item_list", List.of(Map.of("type", 1, "text_item", Map.of("text", text))));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msg", msg);
        payload.put("base_info", Map.of("channel_version", channelVersion));

        HttpRequest req = newRequest(apiBase + "/ilink/bot/sendmessage", botToken)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)))
                .build();
        sendAndParse(req);
    }

    // -----------------------------------------------------------------
    //  Internals
    // -----------------------------------------------------------------

    private HttpRequest.Builder newRequest(String uri, String botToken) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Content-Type", "application/json");
        authHeaders(b, botToken);
        return b;
    }

    private HttpRequest.Builder authHeaders(HttpRequest.Builder b, String botToken) {
        b.header("AuthorizationType", "ilink_bot_token");
        if (botToken != null && !botToken.isBlank()) {
            b.header("Authorization", "Bearer " + botToken);
        }
        // X-WECHAT-UIN is a per-request random uint32 string, base64-encoded (anti-replay).
        byte[] bytes = new byte[4];
        SECURE_RANDOM.nextBytes(bytes);
        long uint32 = ((bytes[0] & 0xFFL) << 24)
                | ((bytes[1] & 0xFFL) << 16)
                | ((bytes[2] & 0xFFL) << 8)
                | (bytes[3] & 0xFFL);
        b.header("X-WECHAT-UIN",
                Base64.getEncoder().encodeToString(String.valueOf(uint32).getBytes(StandardCharsets.UTF_8)));
        return b;
    }

    /** client:{timestampMillis}-{8 random hex chars}, matching the reference SDK. */
    private static String generateClientId() {
        byte[] randomBytes = new byte[4];
        SECURE_RANDOM.nextBytes(randomBytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : randomBytes) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        return "client:" + System.currentTimeMillis() + "-" + hex;
    }

    private JsonNode sendAndParse(HttpRequest req) throws Exception {
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            log.debug("iLink HTTP {} for {}", resp.statusCode(), req.uri().getPath());
            throw new IlinkException(
                    "iLink HTTP " + resp.statusCode() + ": " + truncate(resp.body()), resp.statusCode());
        }
        JsonNode root = JSON.readTree(resp.body());
        // Envelope: {ret, errcode, errmsg, ...}. Non-zero ret/errcode is a business error.
        int ret = root.path("ret").asInt(0);
        int errcode = root.path("errcode").asInt(0);
        if (ret != 0 || errcode != 0) {
            String errmsg = text(root, "errmsg", "errMsg", "message");
            throw new IlinkException(
                    "iLink error ret=" + ret + ", errcode=" + errcode
                            + (errmsg != null ? ", " + errmsg : ""),
                    resp.statusCode());
        }
        return root;
    }

    private static String text(JsonNode node, String... names) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String name : names) {
            JsonNode v = node.get(name);
            if (v != null && !v.isNull()) {
                String s = v.asText(null);
                if (s != null && !s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    private static String normalizeBase(String base) {
        if (base == null || base.isBlank()) {
            return "https://ilinkai.weixin.qq.com";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
