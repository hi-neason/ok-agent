package io.okagent.service.customerwork;

import io.okagent.domain.customerwork.CustomerCaseStatus;
import io.okagent.domain.customerwork.CustomerCaseType;
import io.okagent.domain.dialogue.DialoguePriority;
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
