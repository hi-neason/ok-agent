package io.okagent.web.knowledge;

import java.time.Instant;
import java.util.UUID;

public record AgentKnowledgeBindingResponse(
        UUID id,
        UUID agentId,
        UUID catalogItemId,
        String remoteKnowledgeId,
        String knowledgeName,
        String sourceName,
        String descriptionOverride,
        Integer topK,
        Double scoreThreshold,
        boolean enabled,
        String metadataStatus,
        boolean active,
        Instant updatedAt) {}
