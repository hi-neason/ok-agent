package io.okagent.web.product;

import io.okagent.domain.product.ProductSourceType;
import java.time.Instant;
import java.util.UUID;

public record ProductSourceResponse(
        UUID id,
        String sourceKey,
        String name,
        ProductSourceType sourceType,
        String baseUrl,
        boolean hasSecrets,
        String lastTestStatus,
        String lastTestMessage,
        Instant lastTestedAt,
        Instant lastSyncedAt,
        int productCount,
        boolean enabled,
        Instant updatedAt) {}
