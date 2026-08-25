package io.okagent.service.dialogue;

import io.okagent.domain.dialogue.DialoguePriority;
import io.okagent.domain.dialogue.DialogueWorkStatus;
import java.util.UUID;

/** Optional filters for the operational conversation inbox. */
public record DialogueWorkItemQuery(
        DialogueWorkStatus status,
        DialoguePriority priority,
        UUID assigneeAccountId,
        boolean unassigned,
        String userId,
        UUID agentId) {}
