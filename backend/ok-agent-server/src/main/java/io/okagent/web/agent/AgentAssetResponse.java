package io.okagent.web.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.domain.agent.AgentAsset;
import io.okagent.domain.agent.AgentMemoryFlushMode;
import io.okagent.domain.agent.AgentPermissionMode;
import io.okagent.domain.agent.AgentWorkspaceMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AgentAssetResponse(
        UUID id,
        String agentKey,
        String name,
        String description,
        String businessDomain,
        String systemPrompt,
        String welcomeMessage,
        UUID modelAssetId,
        Double temperature,
        Double topP,
        Integer topK,
        Integer maxTokens,
        int maxIters,
        int modelTimeoutSeconds,
        int toolTimeoutSeconds,
        int maxRetries,
        AgentPermissionMode permissionMode,
        boolean parallelToolCalls,
        boolean compactionEnabled,
        int maxContextTokens,
        boolean toolResultEvictionEnabled,
        boolean tracingEnabled,
        List<UUID> mcpServerIds,
        List<UUID> skillIds,
        Map<String, List<String>> mcpToolFilters,
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
        int sandboxCpuCount,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<UUID>> UUID_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, List<String>>> TOOL_FILTERS = new TypeReference<>() {};

    public static AgentAssetResponse from(AgentAsset a) {
        return new AgentAssetResponse(
                a.getId(),
                a.getAgentKey(),
                a.getName(),
                a.getDescription(),
                a.getBusinessDomain(),
                a.getSystemPrompt(),
                a.getWelcomeMessage(),
                a.getModelAssetId(),
                a.getTemperature(),
                a.getTopP(),
                a.getTopK(),
                a.getMaxTokens(),
                a.getMaxIters(),
                a.getModelTimeoutSeconds(),
                a.getToolTimeoutSeconds(),
                a.getMaxRetries(),
                a.getPermissionMode(),
                a.isParallelToolCalls(),
                a.isCompactionEnabled(),
                a.getMaxContextTokens(),
                a.isToolResultEvictionEnabled(),
                a.isTracingEnabled(),
                readUuidList(a.getMcpServerIdsJson()),
                readUuidList(a.getSkillIdsJson()),
                readToolFilters(a.getMcpToolFiltersJson()),
                a.isMemoryEnabled(),
                a.getMemoryFlushMode(),
                a.getMemoryFlushIntervalMinutes(),
                a.getMemoryConsolidationIntervalMinutes(),
                a.getMemoryDailyRetentionDays(),
                a.getMemorySessionRetentionDays(),
                a.getWorkspaceMode(),
                a.getWorkspaceIsolationScope(),
                a.isWorkspaceContextEnabled(),
                a.isShellEnabled(),
                a.getDockerImage(),
                a.getSandboxMemoryMb(),
                a.getSandboxCpuCount(),
                a.isEnabled(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }

    private static List<UUID> readUuidList(String json) {
        try {
            return JSON.readValue(json, UUID_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Map<String, List<String>> readToolFilters(String json) {
        try {
            return JSON.readValue(json, TOOL_FILTERS);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
