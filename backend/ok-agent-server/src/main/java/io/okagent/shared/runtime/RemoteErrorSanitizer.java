package io.okagent.shared.runtime;

import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * Produces user-facing error text for remote integrations without echoing provider payloads in full.
 */
public final class RemoteErrorSanitizer {
    private static final int RESPONSE_SNIPPET_LIMIT = 160;

    private RemoteErrorSanitizer() {}

    public static String http(String provider, int statusCode, String body, String authenticationHint) {
        if (statusCode == 401 || statusCode == 403) {
            return authenticationHint;
        }
        if (statusCode == 429) {
            return provider + " rate limit reached; please retry later";
        }
        if (statusCode >= 500) {
            return provider + " service is temporarily unavailable (HTTP " + statusCode + ")";
        }
        String snippet = sanitize(body);
        if (snippet.isBlank()) {
            return provider + " request failed (HTTP " + statusCode + ")";
        }
        return provider + " request failed (HTTP " + statusCode + "): " + snippet;
    }

    public static String exception(Throwable failure, String authenticationHint) {
        Throwable root = rootCause(failure);
        if (root instanceof HttpTimeoutException || root instanceof TimeoutException) {
            return "Remote request timed out";
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (message.contains("401") || lower.contains("unauthorized") || lower.contains("forbidden")) {
            return authenticationHint;
        }
        return sanitize(message);
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value
                .replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+", "$1***")
                .replaceAll("(?i)(api[_-]?key[\"'\\s:=]+)[^\"'\\s,}]+", "$1***")
                .replaceAll("(?i)(token[\"'\\s:=]+)[^\"'\\s,}]+", "$1***")
                .replaceAll("(?i)(secret[\"'\\s:=]+)[^\"'\\s,}]+", "$1***")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() <= RESPONSE_SNIPPET_LIMIT) {
            return cleaned;
        }
        return cleaned.substring(0, RESPONSE_SNIPPET_LIMIT) + "...";
    }
}
