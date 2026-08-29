package io.okagent.module.model.application;

import io.okagent.module.model.domain.ModelType;
import jakarta.validation.constraints.*;

public record ModelAssetRequest(
        @NotBlank String name,
        @NotNull ModelType type,
        @NotBlank String provider,
        @NotBlank String modelId,
        @NotBlank String endpoint,
        String apiKey,
        boolean enabled) {}
