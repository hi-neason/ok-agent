package io.okagent.web.workflow;

import io.okagent.domain.workflow.WorkflowSourceType;
import java.time.Instant;
import java.util.UUID;

public record WorkflowSourceResponse(
        UUID id,
        String sourceKey,
        String name,
        WorkflowSourceType sourceType,
        String baseUrl,
        boolean enabled,
        boolean hasApiKey,
        int executeTimeoutSeconds,
        int connectTimeoutSeconds,
        String lastTestStatus,
        String lastTestMessage,
        Instant lastTestedAt,
        Instant lastSyncedAt,
        int workflowCount,
        Instant updatedAt) {}
