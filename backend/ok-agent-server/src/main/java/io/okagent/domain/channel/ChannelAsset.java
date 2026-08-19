package io.okagent.domain.channel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A channel instance binding an external messaging provider (Feishu, etc.) to a single Agent. The
 * {@code channelKey} is both the framework channel id and the callback path segment, so it must be
 * an unguessable random value. Provider-specific non-secret settings live in {@code configJson};
 * secrets (app secret, encrypt key, verification token) are encrypted in {@code secretsCiphertext}
 * via ApiKeyCipher.
 *
 * <p>A channel binds a bot that may serve one or many users; there is no fixed owner. The runtime
 * {@code userId} for each conversation is derived per inbound message from the channel sender
 * (e.g. the Feishu open_id).
 */
@Entity
@Table(name = "channel_asset")
public class ChannelAsset {

    @Id
    private UUID id;

    @Column(name = "channel_key", nullable = false, length = 64)
    private String channelKey;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ChannelType type;

    @Column(name = "bound_agent_id")
    private UUID boundAgentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dm_scope", nullable = false, length = 32)
    private ChannelDmScope dmScope;

    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "secrets_ciphertext", columnDefinition = "TEXT")
    private String secretsCiphertext;

    @Column(name = "secrets_configured_json", nullable = false, columnDefinition = "TEXT")
    private String secretsConfiguredJson;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "runtime_status", nullable = false, length = 16)
    private ChannelRuntimeStatus runtimeStatus;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    protected ChannelAsset() {}

    public ChannelAsset(
            UUID id,
            String channelKey,
            String name,
            ChannelType type,
            UUID boundAgentId,
            ChannelDmScope dmScope,
            String configJson,
            String secretsCiphertext,
            String secretsConfiguredJson,
            boolean enabled,
            String updatedBy) {
        this.id = id;
        this.channelKey = channelKey;
        this.name = name;
        this.type = type;
        this.boundAgentId = boundAgentId;
        this.dmScope = dmScope == null ? ChannelDmScope.PER_PEER : dmScope;
        this.configJson = configJson;
        this.secretsCiphertext = secretsCiphertext;
        this.secretsConfiguredJson = secretsConfiguredJson == null ? "{}" : secretsConfiguredJson;
        this.enabled = enabled;
        this.runtimeStatus = ChannelRuntimeStatus.STOPPED;
        this.updatedBy = updatedBy == null ? "system" : updatedBy;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Replaces the editable configuration. Secrets are only replaced when a non-blank ciphertext is supplied. */
    public void update(
            String name,
            UUID boundAgentId,
            ChannelDmScope dmScope,
            String configJson,
            String secretsCiphertext,
            String secretsConfiguredJson,
            boolean enabled,
            String updatedBy) {
        this.name = name;
        this.boundAgentId = boundAgentId;
        this.dmScope = dmScope == null ? ChannelDmScope.PER_PEER : dmScope;
        this.configJson = configJson;
        if (secretsCiphertext != null && !secretsCiphertext.isBlank()) {
            this.secretsCiphertext = secretsCiphertext;
        }
        if (secretsConfiguredJson != null && !secretsConfiguredJson.isBlank()) {
            this.secretsConfiguredJson = secretsConfiguredJson;
        }
        this.enabled = enabled;
        this.updatedBy = updatedBy == null ? "system" : updatedBy;
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    /** Updates the runtime lifecycle status and last error observed by the channel runtime manager. */
    public void reportRuntime(ChannelRuntimeStatus status, String error) {
        this.runtimeStatus = status;
        this.lastError = error;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getChannelKey() {
        return channelKey;
    }

    public String getName() {
        return name;
    }

    public ChannelType getType() {
        return type;
    }

    public UUID getBoundAgentId() {
        return boundAgentId;
    }

    public ChannelDmScope getDmScope() {
        return dmScope;
    }

    public String getConfigJson() {
        return configJson;
    }

    public String getSecretsCiphertext() {
        return secretsCiphertext;
    }

    public String getSecretsConfiguredJson() {
        return secretsConfiguredJson;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ChannelRuntimeStatus getRuntimeStatus() {
        return runtimeStatus;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
