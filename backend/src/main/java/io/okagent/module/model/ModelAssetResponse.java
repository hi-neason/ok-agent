package io.okagent.module.model;

import java.time.Instant;

public record ModelAssetResponse(
        String id, String name, ModelType type, String provider, String modelId,
        String endpoint, String secretRef, boolean enabled, Instant updatedAt) {}
