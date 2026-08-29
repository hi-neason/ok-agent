package io.okagent.module.intent.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Flat representation of an intent node, exposed to the UI. */
public record IntentDto(
        UUID id,
        UUID parentId,
        String intentKey,
        String name,
        String description,
        List<String> examples,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt) {}
