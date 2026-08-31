package io.okagent.module.observe.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TracePayloadSanitizerTests {
    private final TracePayloadSanitizer sanitizer = new TracePayloadSanitizer(new ObjectMapper());

    @Test
    void recursivelyRedactsSensitiveJsonFields() {
        String payload = """
                {"apiKey":"key-value","headers":{"Authorization":"Bearer token-value"},
                 "nested":[{"refresh_token":"refresh-value","name":"visible"}]}
                """;

        String sanitized = sanitizer.sanitize(payload);

        assertThat(sanitized)
                .doesNotContain("key-value", "token-value", "refresh-value")
                .contains("\"apiKey\":\"[REDACTED]\"")
                .contains("\"name\":\"visible\"");
    }

    @Test
    void redactsCredentialsEmbeddedInPlainText() {
        String payload = "request Authorization: Bearer abc.def and api_key=query-secret and sk-abcdefghijk";

        String sanitized = sanitizer.sanitize(payload);

        assertThat(sanitized)
                .doesNotContain("abc.def", "query-secret", "sk-abcdefghijk")
                .contains("Bearer [REDACTED]", "api_key=[REDACTED]");
    }

    @Test
    void redactsStructuredSecretsWhenPayloadIsMalformedJson() {
        String payload = "{\"apiKey\":\"leaked-secret\", broken";

        assertThat(sanitizer.sanitize(payload))
                .doesNotContain("leaked-secret")
                .contains("\"apiKey\":\"[REDACTED]\"");
    }
}
