package io.okagent.domain.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-agent product visibility and capability binding. One row per agent: {@code scope} +
 * {@code scopeValue} define which products the agent can see, and {@code capabilitiesJson} (a JSON
 * array of ProductCapability names) defines which product tools are exposed. Customer-service
 * agents typically hold QUERY only; sales agents hold QUERY + RECOMMEND + SOLUTION.
 */
@Entity
@Table(name = "agent_product_binding")
public class AgentProductBinding {
    @Id
    private UUID id;

    @Column(name = "agent_id", nullable = false, unique = true)
    private UUID agentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProductBindingScope scope;

    /** Meaning depends on scope: category string for CATEGORY, JSON array for TAG/EXPLICIT, null for ALL/NONE. */
    @Column(name = "scope_value", columnDefinition = "TEXT")
    private String scopeValue;

    /** JSON array of ProductCapability names the agent may use. */
    @Column(name = "capabilities_json", nullable = false, columnDefinition = "TEXT")
    private String capabilitiesJson;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentProductBinding() {}

    public AgentProductBinding(
            UUID id,
            UUID agentId,
            ProductBindingScope scope,
            String scopeValue,
            String capabilitiesJson) {
        this.id = id;
        this.agentId = agentId;
        this.scope = scope == null ? ProductBindingScope.ALL : scope;
        this.scopeValue = scopeValue;
        this.capabilitiesJson = capabilitiesJson == null || capabilitiesJson.isBlank() ? "[]" : capabilitiesJson;
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void apply(ProductBindingScope scope, String scopeValue, String capabilitiesJson, boolean enabled) {
        this.scope = scope == null ? ProductBindingScope.ALL : scope;
        this.scopeValue = scopeValue;
        this.capabilitiesJson = capabilitiesJson == null || capabilitiesJson.isBlank() ? "[]" : capabilitiesJson;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAgentId() {
        return agentId;
    }

    public ProductBindingScope getScope() {
        return scope;
    }

    public String getScopeValue() {
        return scopeValue;
    }

    public String getCapabilitiesJson() {
        return capabilitiesJson;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
