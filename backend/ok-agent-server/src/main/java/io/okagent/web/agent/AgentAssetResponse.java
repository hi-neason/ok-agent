package io.okagent.web.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.domain.agent.AgentAsset;
import java.time.Instant;
import java.util.List;
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
        List<UUID> mcpServerIds,
        List<UUID> skillIds,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<UUID>> UUID_LIST = new TypeReference<>() {};

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
                readUuidList(a.getMcpServerIdsJson()),
                readUuidList(a.getSkillIdsJson()),
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
}
