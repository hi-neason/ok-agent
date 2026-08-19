package io.okagent.domain.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Agent-level binding to a catalog workflow. Holds only selection and optional agent-local
 * overrides; the input schema lives on {@link WorkflowCatalogItem} and is never modified here.
 */
@Entity
@Table(name = "agent_workflow_binding")
public class AgentWorkflowBinding {
    @Id
    private UUID id;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "catalog_item_id", nullable = false)
    private UUID catalogItemId;

    @Column(name = "description_override", columnDefinition = "TEXT")
    private String descriptionOverride;

    @Column(name = "parameter_defaults_json", columnDefinition = "TEXT")
    private String parameterDefaultsJson;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentWorkflowBinding() {}

    public AgentWorkflowBinding(
            UUID id, UUID agentId, UUID catalogItemId, String descriptionOverride, String parameterDefaultsJson) {
        this.id = id;
        this.agentId = agentId;
        this.catalogItemId = catalogItemId;
        this.descriptionOverride = descriptionOverride;
        this.parameterDefaultsJson = parameterDefaultsJson;
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(String descriptionOverride, String parameterDefaultsJson) {
        this.descriptionOverride = descriptionOverride;
        this.parameterDefaultsJson = parameterDefaultsJson;
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAgentId() {
        return agentId;
    }

    public UUID getCatalogItemId() {
        return catalogItemId;
    }

    public String getDescriptionOverride() {
        return descriptionOverride;
    }

    public String getParameterDefaultsJson() {
        return parameterDefaultsJson;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
