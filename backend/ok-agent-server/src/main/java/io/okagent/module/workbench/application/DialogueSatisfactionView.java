package io.okagent.module.workbench.application;

import java.time.Instant;
import java.util.UUID;

/** Satisfaction feedback shown in the conversation workspace. */
public record DialogueSatisfactionView(
        String sessionId,
        Integer rating,
        String feedback,
        UUID updatedBy,
        Instant updatedAt,
        long version) {}
