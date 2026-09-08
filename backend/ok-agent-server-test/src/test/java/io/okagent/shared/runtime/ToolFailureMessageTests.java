package io.okagent.shared.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;

class ToolFailureMessageTests {

    @Test
    void classifiesFailuresWithoutExposingMessages() {
        assertThat(ToolFailureMessage.of("search_knowledge", new IllegalArgumentException("bad sk-secret")))
                .isEqualTo("Tool failed: search_knowledge (INVALID_ARGUMENT).")
                .doesNotContain("sk-secret");

        assertThat(ToolFailureMessage.of("start_workflow", new SecurityException("tenant secret denied")))
                .isEqualTo("Tool failed: start_workflow (ACCESS_DENIED).")
                .doesNotContain("tenant secret denied");

        assertThat(ToolFailureMessage.of("get_product", new HttpTimeoutException("timed out with token abc")))
                .isEqualTo("Tool failed: get_product (REMOTE_TIMEOUT).")
                .doesNotContain("token abc");
    }
}
