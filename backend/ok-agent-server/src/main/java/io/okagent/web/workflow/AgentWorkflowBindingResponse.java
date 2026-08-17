package io.okagent.web.workflow;

import java.time.Instant;
import java.util.UUID;

public record AgentWorkflowBindingResponse(
        UUID id,
        UUID agentId,
        UUID catalogItemId,
        String remoteWorkflowId,
        String workflowName,
        String sourceName,
        String descriptionOverride,
        String parameterDefaultsJson,
        boolean enabled,
        String metadataStatus,
        boolean active,
        Instant updatedAt) {}
