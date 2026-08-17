package io.okagent.web.knowledge;

import io.okagent.domain.knowledge.KnowledgeSourceType;
import java.time.Instant;
import java.util.UUID;

public record KnowledgeSourceResponse(
        UUID id,
        String sourceKey,
        String name,
        KnowledgeSourceType sourceType,
        String baseUrl,
        boolean enabled,
        boolean hasApiKey,
        int retrieveTimeoutSeconds,
        int connectTimeoutSeconds,
        String lastTestStatus,
        String lastTestMessage,
        Instant lastTestedAt,
        Instant lastSyncedAt,
        int knowledgeCount,
        Instant updatedAt) {}
