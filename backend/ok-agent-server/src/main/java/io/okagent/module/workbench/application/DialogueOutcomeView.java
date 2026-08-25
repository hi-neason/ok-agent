package io.okagent.module.workbench.application;

import io.okagent.domain.dialogue.CustomerSentiment;
import java.time.Instant;
import java.util.UUID;

/** Structured business outcome returned to the conversation workspace. */
public record DialogueOutcomeView(
        String sessionId,
        String summary,
        String customerNeed,
        String intentLabel,
        String productInterest,
        String budget,
        String purchaseTimeline,
        CustomerSentiment sentiment,
        String resolutionCode,
        String nextAction,
        Instant followUpAt,
        UUID updatedBy,
        Instant updatedAt,
        long version) {}
