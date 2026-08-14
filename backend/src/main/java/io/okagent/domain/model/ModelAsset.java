package io.okagent.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "model_asset")
public class ModelAsset {
  @Id private UUID id;

  @Column(nullable = false, length = 128)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ModelType type;

  @Column(nullable = false, length = 64)
  private String provider;

  @Column(name = "model_id", nullable = false, length = 128)
  private String modelId;

  @Column(nullable = false, length = 1024)
  private String endpoint;

  @Column(name = "secret_ref", nullable = false, length = 255)
  private String secretRef;

  @Column(nullable = false)
  private boolean enabled;

  @Version private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ModelAsset() {}

  public ModelAsset(
      UUID id,
      String name,
      ModelType type,
      String provider,
      String modelId,
      String endpoint,
      String secretRef,
      boolean enabled) {
    this.id = id;
    this.name = name;
    this.type = type;
    this.provider = provider;
    this.modelId = modelId;
    this.endpoint = endpoint;
    this.secretRef = secretRef;
    this.enabled = enabled;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void update(
      String name,
      ModelType type,
      String provider,
      String modelId,
      String endpoint,
      String secretRef,
      boolean enabled) {
    this.name = name;
    this.type = type;
    this.provider = provider;
    this.modelId = modelId;
    this.endpoint = endpoint;
    this.secretRef = secretRef;
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

  public String getName() {
    return name;
  }

  public ModelType getType() {
    return type;
  }

  public String getProvider() {
    return provider;
  }

  public String getModelId() {
    return modelId;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public String getSecretRef() {
    return secretRef;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
