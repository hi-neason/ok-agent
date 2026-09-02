package io.okagent.module.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.module.agent.domain.AgentAsset;
import io.okagent.module.agent.domain.AgentMemoryFlushMode;
import io.okagent.module.agent.domain.AgentPermissionMode;
import io.okagent.module.agent.domain.AgentWorkspaceMode;
import io.okagent.module.agent.domain.PersonaInjectionMode;
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
        String modelName,
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
        boolean personaExtractEnabled,
        PersonaInjectionMode personaInjectionMode,
        String personaPromptTemplate,
        AgentWorkspaceMode workspaceMode,
        String workspaceIsolationScope,
        boolean workspaceContextEnabled,
        boolean shellEnabled,
        String dockerImage,
        int sandboxMemoryMb,
        int sandboxCpuCount,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        String subagentsJson,
        String updatedBy) {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<UUID>> UUID_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, List<String>>> TOOL_FILTERS = new TypeReference<>() {};

    public static AgentAssetResponse from(AgentAsset a, Map<UUID, String> modelNames) {
        return new AgentAssetResponse(
                a.getId(),
                a.getAgentKey(),
                a.getName(),
                a.getDescription(),
                a.getBusinessDomain(),
                a.getSystemPrompt(),
                a.getWelcomeMessage(),
                a.getModelAssetId(),
                a.getModelAssetId() == null ? null : modelNames.get(a.getModelAssetId()),
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
                a.isPersonaExtractEnabled(),
                a.getPersonaInjectionMode(),
                a.getPersonaPromptTemplate(),
                a.getWorkspaceMode(),
                a.getWorkspaceIsolationScope(),
                a.isWorkspaceContextEnabled(),
                a.isShellEnabled(),
                a.getDockerImage(),
                a.getSandboxMemoryMb(),
                a.getSandboxCpuCount(),
                a.isEnabled(),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                a.getSubagentsJson(),
                a.getUpdatedBy());
    }

    private static List<UUID> readUuidList(String json) {
        try {
            return JSON.readValue(json, UUID_LIST);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static Map<String, List<String>> readToolFilters(String json) {
        try {
            return JSON.readValue(json, TOOL_FILTERS);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
