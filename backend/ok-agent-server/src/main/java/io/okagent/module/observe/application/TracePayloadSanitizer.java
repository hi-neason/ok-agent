package io.okagent.module.observe.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Removes common credentials from trace payloads before they are persisted or returned. */
@Component
public class TracePayloadSanitizer {
    static final String REDACTED = "[REDACTED]";

    private static final Pattern BEARER =
            Pattern.compile("(?i)(\\bbearer\\s+)[a-z0-9._~+/=-]+");
    private static final Pattern QUERY_SECRET = Pattern.compile(
            "(?i)((?:api[_-]?key|access[_-]?token|refresh[_-]?token|token|password|secret)=)[^&\\s]+"
    );
    private static final Pattern OPENAI_KEY = Pattern.compile("\\bsk-[a-zA-Z0-9_-]{8,}\\b");

    private final ObjectMapper json = new ObjectMapper();

    public TracePayloadSanitizer() {}

    public String sanitize(String payload) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }
        try {
            JsonNode root = sanitizeNode(json.readTree(payload));
            return json.writeValueAsString(root);
        } catch (Exception ignored) {
            return sanitizeText(payload);
        }
    }

    private JsonNode sanitizeNode(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.properties().forEach(entry -> {
                if (isSensitiveKey(entry.getKey())) {
                    object.set(entry.getKey(), TextNode.valueOf(REDACTED));
                } else {
                    object.set(entry.getKey(), sanitizeNode(entry.getValue()));
                }
            });
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                array.set(i, sanitizeNode(array.get(i)));
            }
        } else if (node instanceof TextNode text) {
            return TextNode.valueOf(sanitizeText(text.textValue()));
        }
        return node;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalized.endsWith("authorization")
                || normalized.endsWith("apikey")
                || normalized.endsWith("token")
                || normalized.endsWith("secret")
                || normalized.endsWith("password")
                || normalized.endsWith("passwd")
                || normalized.endsWith("credential")
                || normalized.endsWith("credentials")
                || normalized.endsWith("privatekey")
                || normalized.equals("cookie")
                || normalized.equals("setcookie");
    }

    private String sanitizeText(String value) {
        String sanitized = BEARER.matcher(value).replaceAll("$1" + REDACTED);
        sanitized = QUERY_SECRET.matcher(sanitized).replaceAll("$1" + REDACTED);
        return OPENAI_KEY.matcher(sanitized).replaceAll(REDACTED);
    }
}
