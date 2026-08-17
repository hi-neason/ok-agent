package io.okagent.web.agent;

import io.okagent.domain.agent.AgentMemoryFlushMode;
import io.okagent.domain.agent.AgentPermissionMode;
import io.okagent.domain.agent.AgentWorkspaceMode;
import io.okagent.domain.agent.PersonaInjectionMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AgentConfigRequest(
        @Size(max = 200000) String systemPrompt,
        @Size(max = 2048) String welcomeMessage,
        UUID modelAssetId,
        @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
        @DecimalMin("0.0") @DecimalMax("1.0") Double topP,
        @Min(1) Integer topK,
        @Min(1) @Max(1_000_000) Integer maxTokens,
        @Min(1) @Max(100) int maxIters,
        @Min(1) @Max(1800) int modelTimeoutSeconds,
        @Min(1) @Max(1800) int toolTimeoutSeconds,
        @Min(0) @Max(10) int maxRetries,
        @NotNull AgentPermissionMode permissionMode,
        boolean parallelToolCalls,
        boolean compactionEnabled,
        @Min(1000) @Max(2_000_000) int maxContextTokens,
        boolean toolResultEvictionEnabled,
        boolean tracingEnabled,
        List<UUID> mcpServerIds,
        List<UUID> skillIds,
        Map<String, List<String>> mcpToolFilters,
        boolean memoryEnabled,
        @NotNull AgentMemoryFlushMode memoryFlushMode,
        @Min(1) @Max(1440) int memoryFlushIntervalMinutes,
        @Min(1) @Max(1440) int memoryConsolidationIntervalMinutes,
        @Min(1) @Max(3650) int memoryDailyRetentionDays,
        @Min(1) @Max(3650) int memorySessionRetentionDays,
        boolean personaExtractEnabled,
        PersonaInjectionMode personaInjectionMode,
        @Size(max = 8000) String personaPromptTemplate,
        @NotNull AgentWorkspaceMode workspaceMode,
        @NotNull String workspaceIsolationScope,
        boolean workspaceContextEnabled,
        boolean shellEnabled,
        @Size(max = 255) String dockerImage,
        @Min(128) @Max(32768) int sandboxMemoryMb,
        @Min(1) @Max(64) int sandboxCpuCount) {}
