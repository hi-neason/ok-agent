package io.okagent.module.workflow.application;

import io.okagent.module.workflow.domain.WorkflowSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record WorkflowSourceRequest(
        @NotBlank @Size(max = 128) String sourceKey,
        @NotBlank @Size(max = 128) String name,
        @NotNull WorkflowSourceType sourceType,
        @NotBlank @Size(max = 2048) String baseUrl,
        @Size(max = 4096) String apiKey,
        @Min(1) @Max(1800) Integer executeTimeoutSeconds,
        @Min(1) @Max(300) Integer connectTimeoutSeconds) {}
