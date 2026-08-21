package io.okagent.web.product;

import io.okagent.domain.product.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductRequest(
        @NotBlank @Size(max = 128) String productKey,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 128) String brand,
        @Size(max = 128) String category,
        BigDecimal priceMin,
        BigDecimal priceMax,
        @Size(max = 8) String currency,
        Map<String, Object> spec,
        String sellingPoints,
        List<String> scenarioTags,
        List<String> imageUrls,
        String description,
        ProductStatus status,
        Integer weight) {}
