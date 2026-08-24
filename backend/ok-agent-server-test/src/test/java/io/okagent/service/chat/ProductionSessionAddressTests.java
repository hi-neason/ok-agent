package io.okagent.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductionSessionAddressTests {

    @Test
    void keepsCompatibleShortKeysAndReturnsTheClientSessionId() {
        var address = IntentRouterService.deriveSessionAddress("web", "session-1");

        assertThat(address.sessionId()).isEqualTo("session-1");
        assertThat(address.storageKey()).isEqualTo("web::session-1");
    }

    @Test
    void hashesLongOrAmbiguousStorageKeysDeterministically() {
        String sessionId = "s".repeat(128);

        var first = IntentRouterService.deriveSessionAddress("channel::nested", sessionId);
        var second = IntentRouterService.deriveSessionAddress("channel::nested", sessionId);

        assertThat(first.sessionId()).isEqualTo(sessionId);
        assertThat(first.storageKey())
                .isEqualTo(second.storageKey())
                .startsWith("ps-")
                .hasSizeLessThanOrEqualTo(64);
    }
}
