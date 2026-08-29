package io.okagent.module.knowledge.application;

import java.util.Map;

/**
 * Decrypted, type-agnostic configuration handed to a {@link KnowledgeProvider}. Implementations read
 * their own values from {@code config} (non-secret) and {@code secrets} (decrypted). Built per call
 * from a persisted source so provider implementations never touch the database.
 */
public record KnowledgeSourceConfig(
        String sourceId,
        String sourceType,
        String baseUrl,
        Map<String, Object> config,
        Map<String, Object> secrets,
        int retrieveTimeoutSeconds,
        int connectTimeoutSeconds) {

    public String secret(String key) {
        var value = secrets.get(key);
        return value == null ? "" : value.toString();
    }

    public int configInt(String key, int fallback) {
        var value = config.get(key);
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }
}
