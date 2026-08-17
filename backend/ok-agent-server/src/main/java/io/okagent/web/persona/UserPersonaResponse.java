package io.okagent.web.persona;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.domain.persona.UserPersona;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UserPersonaResponse(
        String userId,
        UUID agentId,
        List<String> tags,
        Map<String, String> preferences,
        String facts,
        String summary,
        String memory,
        Instant updatedAt) {

    public static UserPersonaResponse from(UserPersona p, String memory, ObjectMapper json) {
        return new UserPersonaResponse(
                p.getUserId(),
                p.getAgentId(),
                parseTags(p.getTagsJson(), json),
                parsePreferences(p.getPreferencesJson(), json),
                p.getFacts(),
                p.getSummary(),
                memory,
                p.getUpdatedAt());
    }

    public static UserPersonaResponse empty(String userId, UUID agentId, String memory) {
        return new UserPersonaResponse(userId, agentId, List.of(), Map.of(), null, null, memory, null);
    }

    private static List<String> parseTags(String value, ObjectMapper json) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return json.readValue(value, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Map<String, String> parsePreferences(String value, ObjectMapper json) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return json.readValue(value, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
