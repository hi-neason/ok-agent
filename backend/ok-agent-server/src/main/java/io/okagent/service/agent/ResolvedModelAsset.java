package io.okagent.service.agent;

import java.util.UUID;

/** Frozen non-secret model configuration plus the managed credential reference used at runtime. */
public record ResolvedModelAsset(UUID assetId, String modelId, String endpoint) {}
