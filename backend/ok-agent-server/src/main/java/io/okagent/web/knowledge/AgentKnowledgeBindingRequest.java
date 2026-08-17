package io.okagent.web.knowledge;

import java.util.UUID;

public record AgentKnowledgeBindingRequest(
        UUID catalogItemId,
        String descriptionOverride,
        Integer topK,
        Double scoreThreshold) {}
