package io.okagent.module.agent.application;

import io.okagent.module.agent.domain.AgentAsset;
import io.okagent.module.agent.domain.AgentMemoryFlushMode;
import io.okagent.module.agent.domain.AgentPermissionMode;
import io.okagent.module.agent.domain.AgentWorkspaceMode;
import io.okagent.module.agent.domain.PersonaInjectionMode;
import java.util.List;
import java.util.UUID;

/**
 * A {@link ResolvedAgentConfig} backed by the editable {@link AgentAsset} draft. Used by the debug
 * runtime so configuration changes take effect on the next session rebuild. Its content hash is
 * derived from the draft's {@code updatedAt} (and child drafts), preserving the existing debug
 * behaviour where any save invalidates cached sessions.
 */
public final class DraftAgentConfig implements ResolvedAgentConfig {

    private final AgentAsset draft;
    private final List<ResolvedSubagent> subagents;
    private final String contentHash;

    public DraftAgentConfig(AgentAsset draft, List<ResolvedSubagent> subagents) {
        this.draft = draft;
        this.subagents = List.copyOf(subagents);
        this.contentHash = computeHash();
    }

    @Override
    public String contentHash() {
        return contentHash;
    }

    private String computeHash() {
        var sb = new StringBuilder();
        sb.append(draft.getId()).append(':').append(draft.getUpdatedAt() == null ? "" : draft.getUpdatedAt());
        for (var child : subagents) {
            sb.append('|').append(child.config().getId()).append(':').append(child.config().contentHash());
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    @Override
    public UUID getId() {
        return draft.getId();
    }

    @Override
    public String getAgentKey() {
        return draft.getAgentKey();
    }

    @Override
    public String getName() {
        return draft.getName();
    }

    @Override
    public String getDescription() {
        return draft.getDescription();
    }

    @Override
    public String getSystemPrompt() {
        return draft.getSystemPrompt();
    }

    @Override
    public String getWelcomeMessage() {
        return draft.getWelcomeMessage();
    }

    @Override
    public UUID getModelAssetId() {
        return draft.getModelAssetId();
    }

    @Override
    public Double getTemperature() {
        return draft.getTemperature();
    }

    @Override
    public Double getTopP() {
        return draft.getTopP();
    }

    @Override
    public Integer getTopK() {
        return draft.getTopK();
    }

    @Override
    public Integer getMaxTokens() {
        return draft.getMaxTokens();
    }

    @Override
    public int getMaxIters() {
        return draft.getMaxIters();
    }

    @Override
    public int getModelTimeoutSeconds() {
        return draft.getModelTimeoutSeconds();
    }

    @Override
    public int getToolTimeoutSeconds() {
        return draft.getToolTimeoutSeconds();
    }

    @Override
    public int getMaxRetries() {
        return draft.getMaxRetries();
    }

    @Override
    public AgentPermissionMode getPermissionMode() {
        return draft.getPermissionMode();
    }

    @Override
    public boolean isParallelToolCalls() {
        return draft.isParallelToolCalls();
    }

    @Override
    public boolean isCompactionEnabled() {
        return draft.isCompactionEnabled();
    }

    @Override
    public int getMaxContextTokens() {
        return draft.getMaxContextTokens();
    }

    @Override
    public boolean isToolResultEvictionEnabled() {
        return draft.isToolResultEvictionEnabled();
    }

    @Override
    public boolean isTracingEnabled() {
        return draft.isTracingEnabled();
    }

    @Override
    public String getMcpServerIdsJson() {
        return draft.getMcpServerIdsJson();
    }

    @Override
    public String getSkillIdsJson() {
        return draft.getSkillIdsJson();
    }

    @Override
    public String getMcpToolFiltersJson() {
        return draft.getMcpToolFiltersJson();
    }

    @Override
    public boolean isMemoryEnabled() {
        return draft.isMemoryEnabled();
    }

    @Override
    public AgentMemoryFlushMode getMemoryFlushMode() {
        return draft.getMemoryFlushMode();
    }

    @Override
    public int getMemoryFlushIntervalMinutes() {
        return draft.getMemoryFlushIntervalMinutes();
    }

    @Override
    public int getMemoryConsolidationIntervalMinutes() {
        return draft.getMemoryConsolidationIntervalMinutes();
    }

    @Override
    public int getMemoryDailyRetentionDays() {
        return draft.getMemoryDailyRetentionDays();
    }

    @Override
    public int getMemorySessionRetentionDays() {
        return draft.getMemorySessionRetentionDays();
    }

    @Override
    public boolean isPersonaExtractEnabled() {
        return draft.isPersonaExtractEnabled();
    }

    @Override
    public PersonaInjectionMode getPersonaInjectionMode() {
        return draft.getPersonaInjectionMode();
    }

    @Override
    public String getPersonaPromptTemplate() {
        return draft.getPersonaPromptTemplate();
    }

    @Override
    public AgentWorkspaceMode getWorkspaceMode() {
        return draft.getWorkspaceMode();
    }

    @Override
    public String getWorkspaceIsolationScope() {
        return draft.getWorkspaceIsolationScope();
    }

    @Override
    public boolean isWorkspaceContextEnabled() {
        return draft.isWorkspaceContextEnabled();
    }

    @Override
    public boolean isShellEnabled() {
        return draft.isShellEnabled();
    }

    @Override
    public String getDockerImage() {
        return draft.getDockerImage();
    }

    @Override
    public int getSandboxMemoryMb() {
        return draft.getSandboxMemoryMb();
    }

    @Override
    public int getSandboxCpuCount() {
        return draft.getSandboxCpuCount();
    }

    @Override
    public List<ResolvedSubagent> getSubagents() {
        return subagents;
    }
}
