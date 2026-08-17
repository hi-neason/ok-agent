package io.okagent.service.knowledge;

import java.util.UUID;

/** A knowledge base available to an agent, with agent-local retrieval overrides applied. */
public record BoundKnowledge(
        UUID catalogItemId,
        UUID sourceId,
        String sourceKey,
        String remoteKnowledgeId,
        String name,
        String description,
        boolean active,
        Integer topK,
        Double scoreThreshold) {}
