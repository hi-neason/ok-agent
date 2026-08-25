package io.okagent.module.workbench.application;

import io.okagent.domain.dialogue.CustomerSentiment;
import java.time.Instant;

/** Editable structured fields captured from a customer conversation. */
public record DialogueOutcomeDraft(
        String summary,
        String customerNeed,
        String intentLabel,
        String productInterest,
        String budget,
        String purchaseTimeline,
        CustomerSentiment sentiment,
        String resolutionCode,
        String nextAction,
        Instant followUpAt) {}
