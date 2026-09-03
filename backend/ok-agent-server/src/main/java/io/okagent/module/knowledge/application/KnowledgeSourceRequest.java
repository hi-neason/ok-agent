package io.okagent.module.knowledge.application;

import io.okagent.module.knowledge.domain.KnowledgeSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record KnowledgeSourceRequest(
        @NotBlank @Size(max = 128) String sourceKey,
        @NotBlank @Size(max = 128) String name,
        @NotNull KnowledgeSourceType sourceType,
        @NotBlank @Size(max = 2048) String baseUrl,
        @Size(max = 4096) String apiKey,
        @Min(1) @Max(1800) Integer retrieveTimeoutSeconds,
        @Min(1) @Max(300) Integer connectTimeoutSeconds) {}
