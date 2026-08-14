package io.okagent.domain.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "skill_asset")
public class SkillAsset {
  @Id private UUID id;

  @Column(name = "skill_key", nullable = false, unique = true, length = 128)
  private String skillKey;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(nullable = false, length = 1024)
  private String description;

  @Column(name = "asset_version", nullable = false, length = 64)
  private String assetVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 32)
  private SkillSourceType sourceType;

  @Column(name = "source_uri", length = 1024)
  private String sourceUri;

  @Column(name = "entry_file", nullable = false, length = 255)
  private String entryFile;

  @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
  private String content;

  @Column(nullable = false)
  private boolean enabled;

  @Version private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SkillAsset() {}

  public SkillAsset(
      UUID id,
      String skillKey,
      String name,
      String description,
      String assetVersion,
      SkillSourceType sourceType,
      String sourceUri,
      String entryFile,
      String content,
      boolean enabled) {
    this.id = id;
    this.skillKey = skillKey;
    this.name = name;
    this.description = description;
    this.assetVersion = assetVersion;
    this.sourceType = sourceType;
    this.sourceUri = sourceUri;
    this.entryFile = entryFile;
    this.content = content;
    this.enabled = enabled;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public void update(
      String skillKey,
      String name,
      String description,
      String assetVersion,
      SkillSourceType sourceType,
      String sourceUri,
      String entryFile,
      String content,
      boolean enabled) {
    this.skillKey = skillKey;
    this.name = name;
    this.description = description;
    this.assetVersion = assetVersion;
    this.sourceType = sourceType;
    this.sourceUri = sourceUri;
    this.entryFile = entryFile;
    this.content = content;
    this.enabled = enabled;
    this.updatedAt = Instant.now();
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getSkillKey() {
    return skillKey;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getAssetVersion() {
    return assetVersion;
  }

  public SkillSourceType getSourceType() {
    return sourceType;
  }

  public String getSourceUri() {
    return sourceUri;
  }

  public String getEntryFile() {
    return entryFile;
  }

  public String getContent() {
    return content;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
