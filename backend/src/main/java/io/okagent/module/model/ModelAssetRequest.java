package io.okagent.module.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ModelAssetRequest(
        @NotBlank String name,
        @NotNull ModelType type,
        @NotBlank String provider,
        @NotBlank String modelId,
        @NotBlank String endpoint,
        @NotBlank String secretRef,
        boolean enabled) {}
