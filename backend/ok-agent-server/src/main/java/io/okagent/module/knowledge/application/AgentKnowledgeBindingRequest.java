package io.okagent.module.knowledge.application;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AgentKnowledgeBindingRequest(
        @NotNull UUID catalogItemId,
        @Size(max = 4000) String descriptionOverride,
        @Min(1) @Max(100) Integer topK,
        @DecimalMin("0.0") @DecimalMax("1.0") Double scoreThreshold) {}
