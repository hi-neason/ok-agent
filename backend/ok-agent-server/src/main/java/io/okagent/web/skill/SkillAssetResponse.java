package io.okagent.web.skill;

import io.okagent.domain.skill.SkillAsset;
import io.okagent.domain.skill.SkillSourceType;
import java.time.Instant;
import java.util.UUID;

public record SkillAssetResponse(
    UUID id,
    String skillKey,
    String name,
    String description,
    String assetVersion,
    SkillSourceType sourceType,
    String sourceUri,
    String entryFile,
    String content,
    boolean enabled,
    Instant updatedAt) {
  public static SkillAssetResponse from(SkillAsset asset) {
    return new SkillAssetResponse(
        asset.getId(),
        asset.getSkillKey(),
        asset.getName(),
        asset.getDescription(),
        asset.getAssetVersion(),
        asset.getSourceType(),
        asset.getSourceUri(),
        asset.getEntryFile(),
        asset.getContent(),
        asset.isEnabled(),
        asset.getUpdatedAt());
  }
}
