package io.okagent.module.conversation.application;

import java.time.Instant;
import java.util.UUID;

/** Read-model for one conversation session, as returned by the observability / history list. */
public record DialogueSummary(
        String sessionId,
        UUID agentId,
        String agentName,
        String title,
        String userId,
        Instant createdAt,
        Instant updatedAt,
        long turnCount) {}
