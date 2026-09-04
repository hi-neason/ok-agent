package io.okagent.module.product.application;

import io.okagent.module.product.domain.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductRequest(
        @NotBlank @Size(max = 128) String productKey,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 128) String brand,
        @Size(max = 128) String category,
        @jakarta.validation.constraints.DecimalMin("0.0") BigDecimal priceMin,
        @jakarta.validation.constraints.DecimalMin("0.0") BigDecimal priceMax,
        @Size(max = 8) @jakarta.validation.constraints.Pattern(regexp = "[A-Z]{3}") String currency,
        @Size(max = 200) Map<@Size(max = 128) String, Object> spec,
        @Size(max = 8000) String sellingPoints,
        @Size(max = 100) List<@Size(max = 128) String> scenarioTags,
        @Size(max = 100) List<@Size(max = 2048) String> imageUrls,
        @Size(max = 20000) String description,
        ProductStatus status,
        @Min(-100000) @Max(100000) Integer weight) {}
