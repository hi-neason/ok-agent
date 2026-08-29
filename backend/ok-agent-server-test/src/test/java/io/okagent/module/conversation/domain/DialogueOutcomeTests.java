package io.okagent.module.conversation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DialogueOutcomeTests {

    @Test
    void normalizesAndAuditsStructuredConversationResult() {
        Instant createdAt = Instant.parse("2026-08-25T01:00:00Z");
        Instant updatedAt = createdAt.plusSeconds(60);
        UUID operator = UUID.randomUUID();
        DialogueOutcome outcome = new DialogueOutcome("session", createdAt);

        outcome.revise(
                "  Customer asked for enterprise pricing.  ",
                "  Needs SSO and an SLA  ",
                "  high-intent  ",
                "Enterprise plan",
                "  $20k  ",
                "This quarter",
                CustomerSentiment.POSITIVE,
                "  qualified  ",
                "  Book a demo  ",
                updatedAt.plusSeconds(3600),
                operator,
                updatedAt);

        assertThat(outcome.getSummary()).isEqualTo("Customer asked for enterprise pricing.");
        assertThat(outcome.getCustomerNeed()).isEqualTo("Needs SSO and an SLA");
        assertThat(outcome.getIntentLabel()).isEqualTo("high-intent");
        assertThat(outcome.getBudget()).isEqualTo("$20k");
        assertThat(outcome.getResolutionCode()).isEqualTo("qualified");
        assertThat(outcome.getNextAction()).isEqualTo("Book a demo");
        assertThat(outcome.getSentiment()).isEqualTo(CustomerSentiment.POSITIVE);
        assertThat(outcome.getUpdatedBy()).isEqualTo(operator);
        assertThat(outcome.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void convertsBlankFieldsAndMissingSentimentToSafeDefaults() {
        DialogueOutcome outcome = new DialogueOutcome("session", Instant.EPOCH);

        outcome.revise(
                " ", null, "\n", null, null, null, null, null, null, null, UUID.randomUUID(), Instant.EPOCH);

        assertThat(outcome.getSummary()).isNull();
        assertThat(outcome.getCustomerNeed()).isNull();
        assertThat(outcome.getIntentLabel()).isNull();
        assertThat(outcome.getSentiment()).isEqualTo(CustomerSentiment.UNKNOWN);
    }
}
