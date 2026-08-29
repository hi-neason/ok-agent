package io.okagent.module.product.application;

import io.okagent.module.product.domain.ProductSourceType;
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
