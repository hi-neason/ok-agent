package io.okagent.domain.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Editable draft of a HarnessAgent configuration. The runtime plane never reads this entity
 * directly; it is resolved into an immutable ReleaseSnapshot at release time.
 */
@Entity
@Table(name = "agent_asset")
public class AgentAsset {
    @Id
    private UUID id;

    @Column(name = "agent_key", nullable = false, unique = true, length = 128)
    private String agentKey;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 1024)
    private String description;

    @Column(name = "business_domain", nullable = false, length = 64)
    private String businessDomain;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String systemPrompt;

    @Column(name = "welcome_message", nullable = false, length = 2048)
    private String welcomeMessage;

    @Column(name = "model_asset_id")
    private UUID modelAssetId;

    private Double temperature;

    @Column(name = "top_p")
    private Double topP;

    @Column(name = "top_k")
    private Integer topK;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    /** JSON array of bound MCP server ids. */
    @Column(name = "mcp_server_ids_json", nullable = false, columnDefinition = "TEXT")
    private String mcpServerIdsJson;

    /** JSON array of bound skill ids. */
    @Column(name = "skill_ids_json", nullable = false, columnDefinition = "TEXT")
    private String skillIdsJson;

    @Column(nullable = false)
    private boolean enabled;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentAsset() {}

    public AgentAsset(UUID id, String agentKey, String name, String description, String businessDomain) {
        this.id = id;
        this.agentKey = agentKey;
        this.name = name;
        this.description = description;
        this.businessDomain = businessDomain;
        this.systemPrompt = "";
        this.welcomeMessage = "";
        this.temperature = 0.7;
        this.topP = null;
        this.topK = null;
        this.maxTokens = null;
        this.mcpServerIdsJson = "[]";
        this.skillIdsJson = "[]";
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void updateBasicInfo(String name, String description, String businessDomain) {
        this.name = name;
        this.description = description;
        this.businessDomain = businessDomain;
        this.updatedAt = Instant.now();
    }

    /** Applies the editable HarnessAgent configuration (prompt, model, parameters, bindings). */
    public void updateConfiguration(
            String systemPrompt,
            String welcomeMessage,
            UUID modelAssetId,
            Double temperature,
            Double topP,
            Integer topK,
            Integer maxTokens,
            String mcpServerIdsJson,
            String skillIdsJson) {
        this.systemPrompt = systemPrompt;
        this.welcomeMessage = welcomeMessage;
        this.modelAssetId = modelAssetId;
        this.temperature = temperature;
        this.topP = topP;
        this.topK = topK;
        this.maxTokens = maxTokens;
        this.mcpServerIdsJson = mcpServerIdsJson;
        this.skillIdsJson = skillIdsJson;
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAgentKey() {
        return agentKey;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getBusinessDomain() {
        return businessDomain;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public UUID getModelAssetId() {
        return modelAssetId;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public Integer getTopK() {
        return topK;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public String getMcpServerIdsJson() {
        return mcpServerIdsJson;
    }

    public String getSkillIdsJson() {
        return skillIdsJson;
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
