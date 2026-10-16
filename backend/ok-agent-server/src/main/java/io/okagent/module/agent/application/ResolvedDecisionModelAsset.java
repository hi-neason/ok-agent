package io.okagent.module.agent.application;

import java.util.UUID;

/** Immutable decision model configuration; credentials are never embedded in snapshots. */
public record ResolvedDecisionModelAsset(UUID assetId, String provider, String modelId, String endpoint) {}
