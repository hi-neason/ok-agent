package io.okagent.web.model;

import io.okagent.domain.model.ModelType;
import jakarta.validation.constraints.*;

public record ModelAssetRequest(
    @NotBlank String name,
    @NotNull ModelType type,
    @NotBlank String provider,
    @NotBlank String modelId,
    @NotBlank String endpoint,
    @NotBlank String secretRef,
    boolean enabled) {}
