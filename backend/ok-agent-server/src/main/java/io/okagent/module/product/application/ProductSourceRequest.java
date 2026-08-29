package io.okagent.module.product.application;

import io.okagent.module.product.domain.ProductSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record ProductSourceRequest(
        @NotBlank @Size(max = 128) String sourceKey,
        @NotBlank @Size(max = 128) String name,
        @NotNull ProductSourceType sourceType,
        @Size(max = 2048) String baseUrl,
        String configJson,
        Map<String, String> secrets) {}
