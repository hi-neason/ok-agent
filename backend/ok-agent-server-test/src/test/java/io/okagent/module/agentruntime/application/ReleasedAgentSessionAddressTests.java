package io.okagent.module.agentruntime.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReleasedAgentSessionAddressTests {

    @Test
    void keepsCompatibleShortKeysAndReturnsTheClientSessionId() {
        var address = ReleasedAgentChatService.deriveSessionAddress("web", "session-1");

        assertThat(address.sessionId()).isEqualTo("session-1");
        assertThat(address.storageKey()).isEqualTo("web::session-1");
    }

    @Test
    void hashesLongOrAmbiguousStorageKeysDeterministically() {
        String sessionId = "s".repeat(128);

        var first = ReleasedAgentChatService.deriveSessionAddress("channel::nested", sessionId);
        var second = ReleasedAgentChatService.deriveSessionAddress("channel::nested", sessionId);

        assertThat(first.sessionId()).isEqualTo(sessionId);
        assertThat(first.storageKey())
                .isEqualTo(second.storageKey())
                .startsWith("ps-")
                .hasSizeLessThanOrEqualTo(64);
    }
}
