package io.okagent.domain.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Column(name = "max_iters", nullable = false)
    private int maxIters;

    @Column(name = "model_timeout_seconds", nullable = false)
    private int modelTimeoutSeconds;

    @Column(name = "tool_timeout_seconds", nullable = false)
    private int toolTimeoutSeconds;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_mode", nullable = false, length = 32)
    private AgentPermissionMode permissionMode;

    @Column(name = "parallel_tool_calls", nullable = false)
    private boolean parallelToolCalls;

    @Column(name = "compaction_enabled", nullable = false)
    private boolean compactionEnabled;

    @Column(name = "max_context_tokens", nullable = false)
    private int maxContextTokens;

    @Column(name = "tool_result_eviction_enabled", nullable = false)
    private boolean toolResultEvictionEnabled;

    @Column(name = "tracing_enabled", nullable = false)
    private boolean tracingEnabled;

    /** JSON array of bound MCP server ids. */
    @Column(name = "mcp_server_ids_json", nullable = false, columnDefinition = "TEXT")
    private String mcpServerIdsJson;

    /** JSON array of bound skill ids. */
    @Column(name = "skill_ids_json", nullable = false, columnDefinition = "TEXT")
    private String skillIdsJson;

    /** JSON object keyed by MCP server id with an allowlist of tool names. */
    @Column(name = "mcp_tool_filters_json", nullable = false, columnDefinition = "TEXT")
    private String mcpToolFiltersJson;

    @Column(name = "memory_enabled", nullable = false)
    private boolean memoryEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "memory_flush_mode", nullable = false, length = 32)
    private AgentMemoryFlushMode memoryFlushMode;

    @Column(name = "memory_flush_interval_minutes", nullable = false)
    private int memoryFlushIntervalMinutes;

    @Column(name = "memory_consolidation_interval_minutes", nullable = false)
    private int memoryConsolidationIntervalMinutes;

    @Column(name = "memory_daily_retention_days", nullable = false)
    private int memoryDailyRetentionDays;

    @Column(name = "memory_session_retention_days", nullable = false)
    private int memorySessionRetentionDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "workspace_mode", nullable = false, length = 32)
    private AgentWorkspaceMode workspaceMode;

    @Column(name = "workspace_isolation_scope", nullable = false, length = 16)
    private String workspaceIsolationScope;

    @Column(name = "workspace_context_enabled", nullable = false)
    private boolean workspaceContextEnabled;

    @Column(name = "shell_enabled", nullable = false)
    private boolean shellEnabled;

    @Column(name = "docker_image", nullable = false, length = 255)
    private String dockerImage;

    @Column(name = "sandbox_memory_mb", nullable = false)
    private int sandboxMemoryMb;

    @Column(name = "sandbox_cpu_count", nullable = false)
    private int sandboxCpuCount;

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
        this.maxIters = 10;
        this.modelTimeoutSeconds = 120;
        this.toolTimeoutSeconds = 60;
        this.maxRetries = 2;
        this.permissionMode = AgentPermissionMode.BYPASS;
        this.parallelToolCalls = true;
        this.compactionEnabled = true;
        this.maxContextTokens = 8000;
        this.toolResultEvictionEnabled = true;
        this.tracingEnabled = true;
        this.mcpServerIdsJson = "[]";
        this.skillIdsJson = "[]";
        this.mcpToolFiltersJson = "{}";
        this.memoryEnabled = false;
        this.memoryFlushMode = AgentMemoryFlushMode.THROTTLED;
        this.memoryFlushIntervalMinutes = 30;
        this.memoryConsolidationIntervalMinutes = 30;
        this.memoryDailyRetentionDays = 90;
        this.memorySessionRetentionDays = 180;
        this.workspaceMode = AgentWorkspaceMode.DISABLED;
        this.workspaceIsolationScope = "SESSION";
        this.workspaceContextEnabled = true;
        this.shellEnabled = false;
        this.dockerImage = "";
        this.sandboxMemoryMb = 512;
        this.sandboxCpuCount = 1;
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

    /** Applies HarnessAgent execution limits, permissions, context, and observability settings. */
    public void updateRuntimePolicy(
            int maxIters,
            int modelTimeoutSeconds,
            int toolTimeoutSeconds,
            int maxRetries,
            AgentPermissionMode permissionMode,
            boolean parallelToolCalls,
            boolean compactionEnabled,
            int maxContextTokens,
            boolean toolResultEvictionEnabled,
            boolean tracingEnabled) {
        this.maxIters = maxIters;
        this.modelTimeoutSeconds = modelTimeoutSeconds;
        this.toolTimeoutSeconds = toolTimeoutSeconds;
        this.maxRetries = maxRetries;
        this.permissionMode = permissionMode;
        this.parallelToolCalls = parallelToolCalls;
        this.compactionEnabled = compactionEnabled;
        this.maxContextTokens = maxContextTokens;
        this.toolResultEvictionEnabled = toolResultEvictionEnabled;
        this.tracingEnabled = tracingEnabled;
        this.updatedAt = Instant.now();
    }

    /** Applies MCP tool restrictions, memory policy, and workspace isolation settings. */
    public void updateCapabilities(
            String mcpToolFiltersJson,
            boolean memoryEnabled,
            AgentMemoryFlushMode memoryFlushMode,
            int memoryFlushIntervalMinutes,
            int memoryConsolidationIntervalMinutes,
            int memoryDailyRetentionDays,
            int memorySessionRetentionDays,
            AgentWorkspaceMode workspaceMode,
            String workspaceIsolationScope,
            boolean workspaceContextEnabled,
            boolean shellEnabled,
            String dockerImage,
            int sandboxMemoryMb,
            int sandboxCpuCount) {
        this.mcpToolFiltersJson = mcpToolFiltersJson;
        this.memoryEnabled = memoryEnabled;
        this.memoryFlushMode = memoryFlushMode;
        this.memoryFlushIntervalMinutes = memoryFlushIntervalMinutes;
        this.memoryConsolidationIntervalMinutes = memoryConsolidationIntervalMinutes;
        this.memoryDailyRetentionDays = memoryDailyRetentionDays;
        this.memorySessionRetentionDays = memorySessionRetentionDays;
        this.workspaceMode = workspaceMode;
        this.workspaceIsolationScope = workspaceIsolationScope;
        this.workspaceContextEnabled = workspaceContextEnabled;
        this.shellEnabled = shellEnabled;
        this.dockerImage = dockerImage;
        this.sandboxMemoryMb = sandboxMemoryMb;
        this.sandboxCpuCount = sandboxCpuCount;
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

    public int getMaxIters() {
        return maxIters;
    }

    public int getModelTimeoutSeconds() {
        return modelTimeoutSeconds;
    }

    public int getToolTimeoutSeconds() {
        return toolTimeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public AgentPermissionMode getPermissionMode() {
        return permissionMode;
    }

    public boolean isParallelToolCalls() {
        return parallelToolCalls;
    }

    public boolean isCompactionEnabled() {
        return compactionEnabled;
    }

    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    public boolean isToolResultEvictionEnabled() {
        return toolResultEvictionEnabled;
    }

    public boolean isTracingEnabled() {
        return tracingEnabled;
    }

    public String getMcpServerIdsJson() {
        return mcpServerIdsJson;
    }

    public String getSkillIdsJson() {
        return skillIdsJson;
    }

    public String getMcpToolFiltersJson() {
        return mcpToolFiltersJson;
    }

    public boolean isMemoryEnabled() {
        return memoryEnabled;
    }

    public AgentMemoryFlushMode getMemoryFlushMode() {
        return memoryFlushMode;
    }

    public int getMemoryFlushIntervalMinutes() {
        return memoryFlushIntervalMinutes;
    }

    public int getMemoryConsolidationIntervalMinutes() {
        return memoryConsolidationIntervalMinutes;
    }

    public int getMemoryDailyRetentionDays() {
        return memoryDailyRetentionDays;
    }

    public int getMemorySessionRetentionDays() {
        return memorySessionRetentionDays;
    }

    public AgentWorkspaceMode getWorkspaceMode() {
        return workspaceMode;
    }

    public String getWorkspaceIsolationScope() {
        return workspaceIsolationScope;
    }

    public boolean isWorkspaceContextEnabled() {
        return workspaceContextEnabled;
    }

    public boolean isShellEnabled() {
        return shellEnabled;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public int getSandboxMemoryMb() {
        return sandboxMemoryMb;
    }

    public int getSandboxCpuCount() {
        return sandboxCpuCount;
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
