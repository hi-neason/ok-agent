package io.okagent.web.model;

import io.okagent.domain.model.ModelAsset;
import io.okagent.domain.model.ModelType;
import java.time.Instant;
import java.util.UUID;

public record ModelAssetResponse(
        UUID id,
        String name,
        ModelType type,
        String provider,
        String modelId,
        String endpoint,
        boolean apiKeyConfigured,
        boolean enabled,
        Instant updatedAt) {
    public static ModelAssetResponse from(ModelAsset a) {
        return new ModelAssetResponse(
                a.getId(),
                a.getName(),
                a.getType(),
                a.getProvider(),
                a.getModelId(),
                a.getEndpoint(),
                a.getApiKeyCiphertext() != null && !a.getApiKeyCiphertext().isBlank(),
                a.isEnabled(),
                a.getUpdatedAt());
    }
}
