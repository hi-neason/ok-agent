package io.okagent.module.model.application;

import io.okagent.module.model.domain.ModelType;
import jakarta.validation.constraints.*;

public record ModelAssetRequest(
        @NotBlank @Size(max = 128) String name,
        @NotNull ModelType type,
        @NotBlank @Size(max = 128) String provider,
        @NotBlank @Size(max = 255) String modelId,
        @NotBlank @Size(max = 2048) String endpoint,
        @Size(max = 4096) String apiKey,
        boolean enabled) {}
