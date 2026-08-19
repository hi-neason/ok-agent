package io.okagent.service.persona;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import io.okagent.domain.agent.PersonaInjectionMode;
import io.okagent.domain.persona.UserPersona;
import io.okagent.infrastructure.store.JdbcBaseStore;
import io.okagent.repository.persona.UserPersonaRepository;
import io.okagent.web.persona.UpsertPersonaRequest;
import io.okagent.web.persona.UserPersonaResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UserPersonaServiceImpl implements UserPersonaService {

    private static final String MEMORY_KEY = "MEMORY.md";

    private final UserPersonaRepository repository;
    private final JdbcBaseStore baseStore;
    private final ObjectMapper json = new ObjectMapper();

    public UserPersonaServiceImpl(UserPersonaRepository repository, JdbcBaseStore baseStore) {
        this.repository = repository;
        this.baseStore = baseStore;
    }

    private static List<String> namespace(String userId, UUID agentId) {
        return List.of("users", userId, "persona", agentId.toString());
    }

    @Override
    public UserPersonaResponse getOrInit(String userId, UUID agentId) {
        UserPersona persona =
                repository.findByIdUserIdAndIdAgentId(userId, agentId).orElse(null);
        String memory = readMemory(userId, agentId);
        if (persona == null) {
            return UserPersonaResponse.empty(userId, agentId, memory);
        }
        return UserPersonaResponse.from(persona, memory, json);
    }

    @Override
    public List<UserPersonaResponse> listForUser(String userId) {
        return repository.findByIdUserId(userId).stream()
                .map(p -> UserPersonaResponse.from(p, readMemory(userId, p.getAgentId()), json))
                .toList();
    }

    @Override
    public UserPersonaResponse upsert(String userId, UUID agentId, UpsertPersonaRequest request) {
        UserPersona persona = repository
                .findByIdUserIdAndIdAgentId(userId, agentId)
                .orElseGet(() -> new UserPersona(userId, agentId));
        if (request.tags() != null) {
            persona.setTagsJson(writeJson(request.tags()));
        }
        if (request.preferences() != null) {
            persona.setPreferencesJson(writeJson(request.preferences()));
        }
        if (request.facts() != null) {
            persona.setFacts(request.facts());
        }
        if (request.summary() != null) {
            persona.setSummary(request.summary());
        }
        persona.setUpdatedAt(Instant.now());
        UserPersona saved = repository.save(persona);
        return UserPersonaResponse.from(saved, readMemory(userId, agentId), json);
    }

    @Override
    public String readMemory(String userId, UUID agentId) {
        StoreItem item = baseStore.get(namespace(userId, agentId), MEMORY_KEY);
        if (item == null) {
            return "";
        }
        Object content = item.value().get("content");
        return content == null ? "" : content.toString();
    }

    @Override
    public void appendMemory(String userId, UUID agentId, String delta) {
        if (delta == null || delta.isBlank()) {
            return;
        }
        String existing = readMemory(userId, agentId);
        String stamped = (existing.isBlank() ? "" : existing + "\n\n") + "## " + Instant.now() + "\n" + delta.strip();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("content", stamped);
        value.put("encoding", "utf-8");
        value.put("created_at", Instant.now().toString());
        value.put("modified_at", Instant.now().toString());
        baseStore.put(namespace(userId, agentId), MEMORY_KEY, value);
    }

    @Override
    public String getProfileBlock(String userId, UUID agentId, String mode, String template) {
        PersonaInjectionMode injectionMode = parseMode(mode);
        if (injectionMode == PersonaInjectionMode.NONE || agentId == null) {
            return "";
        }
        List<UserPersona> personas = injectionMode == PersonaInjectionMode.GLOBAL
                ? repository.findByIdUserId(userId)
                : repository
                        .findByIdUserIdAndIdAgentId(userId, agentId)
                        .map(List::of)
                        .orElse(List.of());

        // Merge structured fields across the selected persona rows.
        List<String> tags = new ArrayList<>();
        Map<String, String> prefs = new LinkedHashMap<>();
        List<String> summaries = new ArrayList<>();
        List<String> factsParts = new ArrayList<>();
        for (UserPersona p : personas) {
            tags.addAll(parseTags(p.getTagsJson()));
            prefs.putAll(parsePreferences(p.getPreferencesJson()));
            if (p.getSummary() != null && !p.getSummary().isBlank())
                summaries.add(p.getSummary().strip());
            if (p.getFacts() != null && !p.getFacts().isBlank())
                factsParts.add(p.getFacts().strip());
        }
        List<String> tagsDedup = new ArrayList<>(new LinkedHashSet<>(tags));
        String summary = String.join(" / ", summaries);
        String facts = String.join("\n", factsParts);

        // Memory: own memory for SELF_ONLY; concatenate each agent's memory for GLOBAL.
        StringBuilder memory = new StringBuilder();
        for (UserPersona p : personas) {
            String m = readMemory(userId, p.getAgentId());
            if (!m.isBlank()) {
                if (memory.length() > 0) memory.append("\n\n");
                if (injectionMode == PersonaInjectionMode.GLOBAL) {
                    memory.append("[Agent ").append(p.getAgentId()).append("]\n");
                }
                memory.append(m);
            }
        }

        if (summary.isBlank() && tagsDedup.isEmpty() && prefs.isEmpty() && facts.isBlank() && memory.length() == 0) {
            return "";
        }

        if (template != null && !template.isBlank()) {
            return template.replace("{summary}", summary)
                    .replace("{tags}", writeJsonSafe(tagsDedup))
                    .replace("{preferences}", writeJsonSafe(prefs))
                    .replace("{facts}", facts)
                    .replace("{memory}", memory.toString());
        }
        StringBuilder sb = new StringBuilder("# 用户画像 (User Profile)\n");
        if (!summary.isBlank()) sb.append("总结: ").append(summary).append("\n");
        if (!tagsDedup.isEmpty())
            sb.append("标签: ").append(writeJsonSafe(tagsDedup)).append("\n");
        if (!prefs.isEmpty()) sb.append("偏好: ").append(writeJsonSafe(prefs)).append("\n");
        if (!facts.isBlank()) sb.append("关键事实: ").append(facts).append("\n");
        if (memory.length() > 0) sb.append("长期记忆:\n").append(memory).append("\n");
        return sb.toString();
    }

    private static PersonaInjectionMode parseMode(String mode) {
        if (mode == null) return PersonaInjectionMode.NONE;
        try {
            return PersonaInjectionMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PersonaInjectionMode.NONE;
        }
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize persona json", e);
        }
    }

    private String writeJsonSafe(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            return "";
        }
    }

    private List<String> parseTags(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return json.readValue(value, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, String> parsePreferences(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return json.readValue(value, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
