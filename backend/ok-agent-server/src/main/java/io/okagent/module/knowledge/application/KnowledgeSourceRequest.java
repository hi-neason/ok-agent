package io.okagent.module.knowledge.application;

import io.okagent.module.knowledge.domain.KnowledgeSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KnowledgeSourceRequest(
        @NotBlank @Size(max = 128) String sourceKey,
        @NotBlank @Size(max = 128) String name,
        @NotNull KnowledgeSourceType sourceType,
        @NotBlank String baseUrl,
        String apiKey,
        Integer retrieveTimeoutSeconds,
        Integer connectTimeoutSeconds) {}
