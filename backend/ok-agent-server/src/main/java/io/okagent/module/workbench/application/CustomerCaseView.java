package io.okagent.module.workbench.application;

import io.okagent.module.workbench.domain.CustomerCaseStatus;
import io.okagent.module.workbench.domain.CustomerCaseType;
import io.okagent.module.conversation.domain.DialoguePriority;
import java.time.Instant;
import java.util.UUID;

/** Lead or ticket projection displayed next to its source conversation. */
public record CustomerCaseView(
        UUID id,
        CustomerCaseType type,
        CustomerCaseStatus status,
        String title,
        String customerUserId,
        String sourceSessionId,
        String description,
        DialoguePriority priority,
        UUID ownerAccountId,
        Instant createdAt) {}
