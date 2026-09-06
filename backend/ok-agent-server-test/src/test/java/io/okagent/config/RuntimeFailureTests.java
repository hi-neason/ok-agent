package io.okagent.config;
import static org.assertj.core.api.Assertions.*;
import io.okagent.shared.runtime.RuntimeFailure;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
class RuntimeFailureTests {
    @Test void classifiesTimeoutsAndNeverExposesProviderBodies() {
        var timeout = RuntimeFailure.from(new IllegalStateException("secret", new java.util.concurrent.TimeoutException()), "trace");
        assertThat(timeout.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(timeout.code()).isEqualTo("RUNTIME_TIMEOUT");
        var upstream = RuntimeFailure.from(new RuntimeException("provider secret payload"), "trace");
        assertThat(upstream.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(upstream.getMessage()).doesNotContain("secret", "payload");
        assertThat(upstream.traceId()).isEqualTo("trace");
    }
}
