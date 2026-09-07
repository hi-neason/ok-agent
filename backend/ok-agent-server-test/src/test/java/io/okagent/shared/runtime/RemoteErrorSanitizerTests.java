package io.okagent.shared.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;

class RemoteErrorSanitizerTests {

    @Test
    void mapsAuthFailuresToProviderHint() {
        assertThat(RemoteErrorSanitizer.http(
                        "Dify", 401, "{\"api_key\":\"sk-secret\",\"message\":\"unauthorized\"}", "check key"))
                .isEqualTo("check key");
        assertThat(RemoteErrorSanitizer.exception(new IllegalStateException("401 unauthorized"), "check key"))
                .isEqualTo("check key");
    }

    @Test
    void removesCommonSecretFragmentsFromVisibleRemoteBodies() {
        String message = RemoteErrorSanitizer.http(
                "Dify",
                400,
                "{\"api_key\":\"sk-secret\",\"token\":\"tok-secret\",\"secret\":\"very-secret\",\"detail\":\"bad input\"}",
                "check key");

        assertThat(message).contains("Dify request failed (HTTP 400)");
        assertThat(message).contains("api_key\":\"***");
        assertThat(message).contains("token\":\"***");
        assertThat(message).contains("secret\":\"***");
        assertThat(message).doesNotContain("sk-secret", "tok-secret", "very-secret");
    }

    @Test
    void collapsesProviderOutagesAndTimeouts() {
        assertThat(RemoteErrorSanitizer.http("Dify", 502, "<html>provider stack trace</html>", "check key"))
                .isEqualTo("Dify service is temporarily unavailable (HTTP 502)");
        assertThat(RemoteErrorSanitizer.exception(new HttpTimeoutException("timed out"), "check key"))
                .isEqualTo("Remote request timed out");
    }
}
