package io.okagent.web.product;

import io.okagent.domain.product.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String productKey,
        UUID sourceId,
        String externalId,
        String name,
        String brand,
        String category,
        BigDecimal priceMin,
        BigDecimal priceMax,
        String currency,
        Map<String, Object> spec,
        String sellingPoints,
        List<String> scenarioTags,
        List<String> imageUrls,
        String description,
        ProductStatus status,
        int weight,
        long version,
        Instant createdAt,
        Instant updatedAt) {}
