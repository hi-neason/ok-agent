package io.okagent.service.agent;

import io.okagent.domain.agent.AgentMemoryFlushMode;
import io.okagent.domain.agent.AgentPermissionMode;
import io.okagent.domain.agent.AgentWorkspaceMode;
import io.okagent.domain.agent.PersonaInjectionMode;
import java.util.List;
import java.util.UUID;

/**
 * A runtime-neutral view of everything {@link HarnessAgentFactory} needs to build a
 * {@link io.agentscope.harness.agent.HarnessAgent}. Two implementations exist:
 *
 * <ul>
 *   <li><b>Draft</b> — backed by the editable {@code AgentAsset}; used by the debug runtime so
 *       changes are reflected immediately.
 *   <li><b>Release</b> — backed by an immutable version snapshot; used by production traffic so
 *       the runtime never reads a draft and sub-agents run the pinned version, not whatever is
 *       currently edited.
 * </ul>
 *
 * <p>Global reusable assets (model, skills, MCP servers) are still referenced by id and loaded at
 * build time; their content hashes are available for drift detection.
 */
public interface ResolvedAgentConfig {

    UUID getId();

    String getAgentKey();

    String getName();

    String getDescription();

    String getSystemPrompt();

    String getWelcomeMessage();

    UUID getModelAssetId();

    Double getTemperature();

    Double getTopP();

    Integer getTopK();

    Integer getMaxTokens();

    int getMaxIters();

    int getModelTimeoutSeconds();

    int getToolTimeoutSeconds();

    int getMaxRetries();

    AgentPermissionMode getPermissionMode();

    boolean isParallelToolCalls();

    boolean isCompactionEnabled();

    int getMaxContextTokens();

    boolean isToolResultEvictionEnabled();

    boolean isTracingEnabled();

    String getMcpServerIdsJson();

    String getSkillIdsJson();

    String getMcpToolFiltersJson();

    boolean isMemoryEnabled();

    AgentMemoryFlushMode getMemoryFlushMode();

    int getMemoryFlushIntervalMinutes();

    int getMemoryConsolidationIntervalMinutes();

    int getMemoryDailyRetentionDays();

    int getMemorySessionRetentionDays();

    boolean isPersonaExtractEnabled();

    PersonaInjectionMode getPersonaInjectionMode();

    String getPersonaPromptTemplate();

    AgentWorkspaceMode getWorkspaceMode();

    String getWorkspaceIsolationScope();

    boolean isWorkspaceContextEnabled();

    boolean isShellEnabled();

    String getDockerImage();

    int getSandboxMemoryMb();

    int getSandboxCpuCount();

    /**
     * The sub-agents this router delegates to, already resolved into their own configs. For a
     * release these are pinned version snapshots; for a draft they are the current child drafts.
     */
    List<ResolvedSubagent> getSubagents();

    /** A content hash covering this config and (recursively) its pinned sub-agent configs. */
    String contentHash();
}
