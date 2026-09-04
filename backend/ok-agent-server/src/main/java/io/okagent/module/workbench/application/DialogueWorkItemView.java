package io.okagent.module.workbench.application;

import io.okagent.module.conversation.domain.DialoguePriority;
import io.okagent.module.conversation.domain.DialogueWorkStatus;
import java.time.Instant;
import java.util.UUID;

/** Operational read model for one conversation in the shared service inbox. */
public record DialogueWorkItemView(
        String sessionId,
        UUID agentId,
        String agentName,
        String title,
        String userId,
        String customerName,
        DialogueWorkStatus status,
        DialoguePriority priority,
        UUID assigneeAccountId,
        String assigneeName,
        Instant handoffRequestedAt,
        Instant assignedAt,
        Instant resolvedAt,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt,
        long turnCount,
        long version,
        String channelType) {}
