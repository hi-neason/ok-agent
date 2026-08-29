package io.okagent.module.conversation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DialogueSatisfactionTests {

    @Test
    void recordsAndNormalizesFivePointFeedback() {
        UUID operator = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-25T03:00:00Z");

        DialogueSatisfaction satisfaction =
                new DialogueSatisfaction("session", 5, "  Fast and helpful  ", operator, now);

        assertThat(satisfaction.getRating()).isEqualTo(5);
        assertThat(satisfaction.getFeedback()).isEqualTo("Fast and helpful");
        assertThat(satisfaction.getUpdatedBy()).isEqualTo(operator);
        assertThat(satisfaction.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void rejectsRatingsOutsidePublicScale() {
        assertThatThrownBy(() -> new DialogueSatisfaction(
                        "session", 0, null, UUID.randomUUID(), Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SATISFACTION_RATING_OUT_OF_RANGE");
        assertThatThrownBy(() -> new DialogueSatisfaction(
                        "session", 6, null, UUID.randomUUID(), Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SATISFACTION_RATING_OUT_OF_RANGE");
    }
}
