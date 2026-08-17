package io.okagent.domain.knowledge;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A registered external knowledge-base system (a "source"), e.g. a Dify workspace. A dataset-level
 * API key can list and retrieve from many knowledge bases. Authentication is stored encrypted.
 */
@Entity
@Table(name = "knowledge_source")
public class KnowledgeSource {
    @Id
    private UUID id;

    @Column(name = "source_key", nullable = false, unique = true, length = 128)
    private String sourceKey;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private KnowledgeSourceType sourceType;

    @Column(name = "base_url", nullable = false, length = 2048)
    private String baseUrl;

    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "secrets_ciphertext", columnDefinition = "TEXT")
    private String secretsCiphertext;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "last_test_status", nullable = false, length = 32)
    private String lastTestStatus;

    @Column(name = "last_test_message", nullable = false, length = 1024)
    private String lastTestMessage;

    @Column(name = "last_tested_at")
    private Instant lastTestedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "knowledge_count", nullable = false)
    private int knowledgeCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KnowledgeSource() {}

    public KnowledgeSource(
            UUID id,
            String sourceKey,
            String name,
            KnowledgeSourceType sourceType,
            String baseUrl,
            String configJson,
            String secretsCiphertext) {
        this.id = id;
        this.sourceKey = sourceKey;
        this.name = name;
        this.sourceType = sourceType;
        this.baseUrl = baseUrl;
        this.configJson = configJson;
        this.secretsCiphertext = secretsCiphertext;
        this.enabled = true;
        this.lastTestStatus = "UNTESTED";
        this.lastTestMessage = "";
        this.knowledgeCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(
            String sourceKey,
            String name,
            KnowledgeSourceType sourceType,
            String baseUrl,
            String configJson,
            String secretsCiphertext) {
        this.sourceKey = sourceKey;
        this.name = name;
        this.sourceType = sourceType;
        this.baseUrl = baseUrl;
        this.configJson = configJson;
        if (secretsCiphertext != null && !secretsCiphertext.isBlank()) {
            this.secretsCiphertext = secretsCiphertext;
        }
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public void recordTest(boolean success, String message) {
        this.lastTestStatus = success ? "SUCCESS" : "FAILED";
        this.lastTestMessage = message == null ? "" : message;
        this.lastTestedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void recordSynced(int knowledgeCount) {
        this.lastSyncedAt = Instant.now();
        this.knowledgeCount = knowledgeCount;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getSourceKey() { return sourceKey; }
    public String getName() { return name; }
    public KnowledgeSourceType getSourceType() { return sourceType; }
    public String getBaseUrl() { return baseUrl; }
    public String getConfigJson() { return configJson; }
    public String getSecretsCiphertext() { return secretsCiphertext; }
    public boolean isEnabled() { return enabled; }
    public String getLastTestStatus() { return lastTestStatus; }
    public String getLastTestMessage() { return lastTestMessage; }
    public Instant getLastTestedAt() { return lastTestedAt; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public int getKnowledgeCount() { return knowledgeCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
