package io.okagent.web.workflow;

import io.okagent.domain.workflow.WorkflowSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkflowSourceRequest(
        @NotBlank @Size(max = 128) String sourceKey,
        @NotBlank @Size(max = 128) String name,
        @NotNull WorkflowSourceType sourceType,
        @NotBlank String baseUrl,
        String apiKey,
        Integer executeTimeoutSeconds,
        Integer connectTimeoutSeconds) {}
