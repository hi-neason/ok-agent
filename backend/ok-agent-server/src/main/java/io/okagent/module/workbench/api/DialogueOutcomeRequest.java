package io.okagent.module.workbench.api;

import io.okagent.module.conversation.domain.CustomerSentiment;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record DialogueOutcomeRequest(
        @Size(max = 8000) String summary,
        @Size(max = 8000) String customerNeed,
        @Size(max = 128) String intentLabel,
        @Size(max = 512) String productInterest,
        @Size(max = 128) String budget,
        @Size(max = 128) String purchaseTimeline,
        CustomerSentiment sentiment,
        @Size(max = 64) String resolutionCode,
        @Size(max = 512) String nextAction,
        Instant followUpAt) {}
