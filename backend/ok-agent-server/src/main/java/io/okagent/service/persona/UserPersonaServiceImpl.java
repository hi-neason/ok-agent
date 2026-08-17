package io.okagent.service.persona;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import io.okagent.domain.persona.UserPersona;
import io.okagent.infrastructure.store.JdbcBaseStore;
import io.okagent.repository.persona.UserPersonaRepository;
import io.okagent.web.persona.AppendMemoryRequest;
import io.okagent.web.persona.UpsertPersonaRequest;
import io.okagent.web.persona.UserPersonaResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class UserPersonaServiceImpl implements UserPersonaService {

    private static final List<String> personaNamespace(String userId) {
        return List.of("users", userId, "persona");
    }

    private static final String MEMORY_KEY = "MEMORY.md";

    private final UserPersonaRepository repository;
    private final JdbcBaseStore baseStore;
    private final ObjectMapper json = new ObjectMapper();

    public UserPersonaServiceImpl(UserPersonaRepository repository, JdbcBaseStore baseStore) {
        this.repository = repository;
        this.baseStore = baseStore;
    }

    @Override
    public UserPersonaResponse getOrInit(String userId) {
        UserPersona persona = repository.findById(userId).orElse(null);
        String memory = readMemory(userId);
        if (persona == null) {
            return UserPersonaResponse.empty(userId, memory);
        }
        return UserPersonaResponse.from(persona, memory, json);
    }

    @Override
    public UserPersonaResponse upsert(String userId, UpsertPersonaRequest request) {
        UserPersona persona = repository.findById(userId).orElseGet(() -> new UserPersona(userId));
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
        return UserPersonaResponse.from(saved, readMemory(userId), json);
    }

    @Override
    public String readMemory(String userId) {
        StoreItem item = baseStore.get(personaNamespace(userId), MEMORY_KEY);
        if (item == null) {
            return "";
        }
        Object content = item.value().get("content");
        return content == null ? "" : content.toString();
    }

    @Override
    public void appendMemory(String userId, String delta) {
        if (delta == null || delta.isBlank()) {
            return;
        }
        String existing = readMemory(userId);
        String stamped =
                (existing.isBlank() ? "" : existing + "\n\n") + "## " + Instant.now() + "\n" + delta.strip();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("content", stamped);
        value.put("encoding", "utf-8");
        value.put("created_at", Instant.now().toString());
        value.put("modified_at", Instant.now().toString());
        baseStore.put(personaNamespace(userId), MEMORY_KEY, value);
    }

    @Override
    public String getProfileBlock(String userId, String template) {
        UserPersona persona = repository.findById(userId).orElse(null);
        String memory = readMemory(userId);
        if (persona == null && memory.isBlank()) {
            return "";
        }
        if (template != null && !template.isBlank()) {
            return template
                    .replace("{summary}", nullToEmpty(persona == null ? null : persona.getSummary()))
                    .replace("{tags}", nullToEmpty(persona == null ? null : persona.getTagsJson()))
                    .replace("{preferences}", nullToEmpty(persona == null ? null : persona.getPreferencesJson()))
                    .replace("{facts}", nullToEmpty(persona == null ? null : persona.getFacts()))
                    .replace("{memory}", memory);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# 用户画像 (User Profile)\n");
        if (persona != null && persona.getSummary() != null) {
            sb.append("总结: ").append(persona.getSummary()).append("\n");
        }
        if (persona != null && persona.getTagsJson() != null) {
            sb.append("标签: ").append(persona.getTagsJson()).append("\n");
        }
        if (persona != null && persona.getPreferencesJson() != null) {
            sb.append("偏好: ").append(persona.getPreferencesJson()).append("\n");
        }
        if (persona != null && persona.getFacts() != null) {
            sb.append("关键事实: ").append(persona.getFacts()).append("\n");
        }
        if (!memory.isBlank()) {
            sb.append("长期记忆:\n").append(memory).append("\n");
        }
        return sb.toString();
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize persona json", e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
