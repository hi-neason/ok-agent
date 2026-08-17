package io.okagent.domain.knowledge;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Agent-level binding to a catalog knowledge base. Holds selection and optional agent-local
 * retrieval overrides (description override, topK, score threshold); the base identity and global
 * description live on {@link KnowledgeCatalogItem}.
 */
@Entity
@Table(name = "agent_knowledge_binding")
public class AgentKnowledgeBinding {
    @Id
    private UUID id;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "catalog_item_id", nullable = false)
    private UUID catalogItemId;

    @Column(name = "description_override", columnDefinition = "TEXT")
    private String descriptionOverride;

    @Column(name = "top_k")
    private Integer topK;

    @Column(name = "score_threshold")
    private Double scoreThreshold;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentKnowledgeBinding() {}

    public AgentKnowledgeBinding(
            UUID id,
            UUID agentId,
            UUID catalogItemId,
            String descriptionOverride,
            Integer topK,
            Double scoreThreshold) {
        this.id = id;
        this.agentId = agentId;
        this.catalogItemId = catalogItemId;
        this.descriptionOverride = descriptionOverride;
        this.topK = topK;
        this.scoreThreshold = scoreThreshold;
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(String descriptionOverride, Integer topK, Double scoreThreshold) {
        this.descriptionOverride = descriptionOverride;
        this.topK = topK;
        this.scoreThreshold = scoreThreshold;
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAgentId() { return agentId; }
    public UUID getCatalogItemId() { return catalogItemId; }
    public String getDescriptionOverride() { return descriptionOverride; }
    public Integer getTopK() { return topK; }
    public Double getScoreThreshold() { return scoreThreshold; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
