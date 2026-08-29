package io.okagent.module.knowledge.application;

import java.util.UUID;

public record AgentKnowledgeBindingRequest(
        UUID catalogItemId, String descriptionOverride, Integer topK, Double scoreThreshold) {}
