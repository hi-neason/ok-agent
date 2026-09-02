package io.okagent.module.release.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.okagent.module.agent.domain.AgentAsset;
import io.okagent.module.mcp.domain.McpServer;
import io.okagent.module.model.domain.ModelAsset;
import io.okagent.module.skill.domain.SkillAsset;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.mcp.infrastructure.persistence.McpServerRepository;
import io.okagent.module.model.infrastructure.persistence.ModelAssetRepository;
import io.okagent.module.release.infrastructure.persistence.AgentVersionRepository;
import io.okagent.module.skill.infrastructure.persistence.SkillAssetRepository;
import io.okagent.module.model.application.ApiKeyCipher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Freezes an editable {@link AgentAsset} draft into a self-contained, immutable snapshot JSON and
 * computes its SHA-256 content hash. The snapshot captures every scalar configuration field, the
 * bound global-asset references with drift fingerprints, and each referenced sub-agent pinned to
 * its latest version with its own snapshot embedded recursively.
 *
 * <p>Sub-agent versions are pinned independently of any target channel: a released main version
 * must be self-consistent no matter which channel it is promoted to. A child agent must have at
 * least one saved version before it can be pinned; otherwise version creation fails with a clear
 * message.
 */
@Service
public class AgentSnapshotService {
    private static final Logger log = LoggerFactory.getLogger(AgentSnapshotService.class);

    private final AgentAssetRepository agents;
    private final AgentVersionRepository versions;
    private final ModelAssetRepository models;
    private final McpServerRepository mcpServers;
    private final SkillAssetRepository skills;
    private final ApiKeyCipher cipher;
    private final ObjectMapper json;

    public AgentSnapshotService(
            AgentAssetRepository agents,
            AgentVersionRepository versions,
            ModelAssetRepository models,
            McpServerRepository mcpServers,
            SkillAssetRepository skills,
            ApiKeyCipher cipher,
            ObjectMapper json) {
        this.agents = agents;
        this.versions = versions;
        this.models = models;
        this.mcpServers = mcpServers;
        this.skills = skills;
        this.cipher = cipher;
        this.json = json;
    }

    public record SnapshotBundle(String snapshotJson, String contentHash, List<PinnedSubagent> pinnedSubagents) {}

    /** A child agent pinned into a snapshot. */
    public record PinnedSubagent(UUID childAgentId, int versionNo, String childAgentKey, List<String> intentKeys) {}

    /**
     * Validates all references and freezes the draft into a snapshot bundle. Throws a 400 with a
     * human-readable message when a referenced asset is missing/disabled, a model/MCP secret cannot
     * be decrypted, or a referenced sub-agent has no version to pin.
     */
    public SnapshotBundle buildSnapshot(AgentAsset draft) {
        validateScalarReferences(draft);
        Set<UUID> visited = new HashSet<>();
        visited.add(draft.getId());
        List<PinnedSubagent> pinned = new ArrayList<>();
        ObjectNode root = freezeAgent(draft, visited, pinned);
        String canonical;
        try {
            canonical = json.writeValueAsString(root);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize snapshot");
        }
        String hash = sha256(canonical);
        root.put("contentHash", hash);
        String snapshotJson;
        try {
            snapshotJson = json.writeValueAsString(root);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize snapshot");
        }
        return new SnapshotBundle(snapshotJson, hash, List.copyOf(pinned));
    }

    /** Validates that the model exists/is enabled and its API key can be decrypted, plus bound MCP/skills. */
    public void validateScalarReferences(AgentAsset draft) {
        if (draft.getModelAssetId() != null) {
            ModelAsset model = models.findById(draft.getModelAssetId())
                    .orElseThrow(() -> bad("引用的模型不存在或已删除"));
            if (!model.isEnabled()) {
                throw bad("引用的模型「" + model.getName() + "」已禁用");
            }
            try {
                cipher.decrypt(model.getApiKeyCiphertext());
            } catch (Exception e) {
                throw bad("引用的模型「" + model.getName() + "」API 密钥无法解密，请重新配置");
            }
        }
        for (UUID id : readUuidList(draft.getMcpServerIdsJson())) {
            McpServer server = mcpServers.findById(id).orElseThrow(() -> bad("引用的 MCP 服务不存在或已删除"));
            if (!server.isEnabled()) {
                throw bad("引用的 MCP 服务「" + server.getName() + "」已禁用");
            }
            if (server.getSecretsCiphertext() != null && !server.getSecretsCiphertext().isBlank()) {
                try {
                    cipher.decrypt(server.getSecretsCiphertext());
                } catch (Exception e) {
                    throw bad("引用的 MCP 服务「" + server.getName() + "」密钥无法解密，请重新配置");
                }
            }
        }
        for (UUID id : readUuidList(draft.getSkillIdsJson())) {
            SkillAsset skill = skills.findById(id).orElseThrow(() -> bad("引用的技能不存在或已删除"));
            if (!skill.isEnabled()) {
                throw bad("引用的技能「" + skill.getName() + "」已禁用");
            }
        }
    }

    /** Recursively freezes an agent's scalar fields and its pinned sub-agent versions. */
    private ObjectNode freezeAgent(AgentAsset agent, Set<UUID> visited, List<PinnedSubagent> pinned) {
        ObjectNode n = json.createObjectNode();
        n.put("agentId", agent.getId().toString());
        n.put("agentKey", agent.getAgentKey());
        n.put("name", agent.getName());
        n.put("description", agent.getDescription());
        n.put("businessDomain", agent.getBusinessDomain());
        n.put("systemPrompt", agent.getSystemPrompt());
        n.put("welcomeMessage", agent.getWelcomeMessage());
        if (agent.getModelAssetId() != null) n.put("modelAssetId", agent.getModelAssetId().toString());
        putDouble(n, "temperature", agent.getTemperature());
        putDouble(n, "topP", agent.getTopP());
        putInt(n, "topK", agent.getTopK());
        putInt(n, "maxTokens", agent.getMaxTokens());
        n.put("maxIters", agent.getMaxIters());
        n.put("modelTimeoutSeconds", agent.getModelTimeoutSeconds());
        n.put("toolTimeoutSeconds", agent.getToolTimeoutSeconds());
        n.put("maxRetries", agent.getMaxRetries());
        n.put("permissionMode", agent.getPermissionMode().name());
        n.put("parallelToolCalls", agent.isParallelToolCalls());
        n.put("compactionEnabled", agent.isCompactionEnabled());
        n.put("maxContextTokens", agent.getMaxContextTokens());
        n.put("toolResultEvictionEnabled", agent.isToolResultEvictionEnabled());
        n.put("tracingEnabled", agent.isTracingEnabled());
        n.set("mcpServerIds", parseArray(agent.getMcpServerIdsJson()));
        n.set("skillIds", parseArray(agent.getSkillIdsJson()));
        n.set("mcpToolFilters", parseObject(agent.getMcpToolFiltersJson()));
        n.put("memoryEnabled", agent.isMemoryEnabled());
        n.put("memoryFlushMode", agent.getMemoryFlushMode().name());
        n.put("memoryFlushIntervalMinutes", agent.getMemoryFlushIntervalMinutes());
        n.put("memoryConsolidationIntervalMinutes", agent.getMemoryConsolidationIntervalMinutes());
        n.put("memoryDailyRetentionDays", agent.getMemoryDailyRetentionDays());
        n.put("memorySessionRetentionDays", agent.getMemorySessionRetentionDays());
        n.put("personaExtractEnabled", agent.isPersonaExtractEnabled());
        n.put("personaInjectionMode", agent.getPersonaInjectionMode().name());
        if (agent.getPersonaPromptTemplate() != null) {
            n.put("personaPromptTemplate", agent.getPersonaPromptTemplate());
        }
        n.put("workspaceMode", agent.getWorkspaceMode().name());
        n.put("workspaceIsolationScope", agent.getWorkspaceIsolationScope());
        n.put("workspaceContextEnabled", agent.isWorkspaceContextEnabled());
        n.put("shellEnabled", agent.isShellEnabled());
        n.put("dockerImage", agent.getDockerImage());
        n.put("sandboxMemoryMb", agent.getSandboxMemoryMb());
        n.put("sandboxCpuCount", agent.getSandboxCpuCount());

        n.set("refs", buildRefs(agent));

        ArrayNode subs = json.createArrayNode();
        for (var entry : parseSubagentRefs(agent).entrySet()) {
            UUID childId = entry.getKey();
            List<String> intentKeys = entry.getValue();
            if (visited.contains(childId)) {
                throw bad("子 Agent 引用存在循环（agentId=" + childId + "），无法冻结版本");
            }
            var childVersion = versions.findTopByAgentIdOrderByVersionNoDesc(childId)
                    .orElseThrow(() -> bad("子 Agent（id=" + childId + "）尚未保存任何版本，请先为其创建版本后再发布主 Agent"));
            AgentAsset child = agents.findById(childId)
                    .orElseThrow(() -> bad("引用的子 Agent 不存在或已删除（id=" + childId + "）"));
            if (!child.isEnabled()) {
                throw bad("引用的子 Agent「" + child.getName() + "」已禁用");
            }
            pinned.add(new PinnedSubagent(childId, childVersion.getVersionNo(), child.getAgentKey(), intentKeys));
            ObjectNode sub = json.createObjectNode();
            sub.put("childAgentId", childId.toString());
            sub.put("childVersionNo", childVersion.getVersionNo());
            ArrayNode keys = json.createArrayNode();
            for (String k : intentKeys) keys.add(k);
            sub.set("intentKeys", keys);
            // Embed the child's own pinned snapshot recursively so the main version is self-contained.
            visited.add(childId);
            sub.set("snapshot", freezeAgent(child, visited, pinned));
            visited.remove(childId);
            subs.add(sub);
        }
        n.set("subagents", subs);
        return n;
    }

    /** Records drift fingerprints for referenced global assets (id + revision/updatedAt). */
    private ObjectNode buildRefs(AgentAsset agent) {
        ObjectNode refs = json.createObjectNode();
        if (agent.getModelAssetId() != null) {
            models.findById(agent.getModelAssetId()).ifPresent(m -> {
                ObjectNode r = json.createObjectNode();
                r.put("id", m.getId().toString());
                r.put("name", m.getName());
                r.put("modelId", m.getModelId());
                r.put("endpoint", m.getEndpoint());
                r.put("updatedAt", m.getUpdatedAt() == null ? "" : m.getUpdatedAt().toString());
                refs.set("model", r);
            });
        }
        ArrayNode mcpArr = json.createArrayNode();
        for (UUID id : readUuidList(agent.getMcpServerIdsJson())) {
            mcpServers.findById(id).ifPresent(s -> {
                ObjectNode r = json.createObjectNode();
                r.put("id", s.getId().toString());
                r.put("name", s.getName());
                r.put("serverKey", s.getServerKey());
                r.put("transport", s.getTransport().name());
                putNullable(r, "serverUrl", s.getServerUrl());
                putNullable(r, "command", s.getCommand());
                r.set("arguments", parseArray(s.getArgumentsJson()));
                r.set("queryParameters", parseObject(s.getQueryParametersJson()));
                r.put("requestTimeoutSeconds", s.getRequestTimeoutSeconds());
                r.put("initializationTimeoutSeconds", s.getInitializationTimeoutSeconds());
                r.put("updatedAt", s.getUpdatedAt() == null ? "" : s.getUpdatedAt().toString());
                mcpArr.add(r);
            });
        }
        refs.set("mcpServers", mcpArr);
        ArrayNode skillArr = json.createArrayNode();
        for (UUID id : readUuidList(agent.getSkillIdsJson())) {
            skills.findById(id).ifPresent(s -> {
                ObjectNode r = json.createObjectNode();
                r.put("id", s.getId().toString());
                r.put("name", s.getName());
                r.put("skillKey", s.getSkillKey());
                r.put("description", s.getDescription());
                r.put("content", s.getContent());
                r.put("archiveSha256", s.getArchiveSha256() == null ? "" : s.getArchiveSha256());
                skillArr.add(r);
            });
        }
        refs.set("skills", skillArr);
        return refs;
    }

    /** Parses {@code [{"agentId":...,"intentKeys":[...]}]} preserving order and unioning duplicate ids. */
    private Map<UUID, List<String>> parseSubagentRefs(AgentAsset agent) {
        Map<UUID, List<String>> out = new LinkedHashMap<>();
        String raw = agent.getSubagentsJson();
        if (raw == null || raw.isBlank() || "[]".equals(raw.trim())) return out;
        try {
            JsonNode arr = json.readTree(raw);
            if (!arr.isArray()) return out;
            for (JsonNode def : arr) {
                String idText = def.path("agentId").asText("").trim();
                if (idText.isEmpty()) continue;
                UUID id;
                try {
                    id = UUID.fromString(idText);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (id.equals(agent.getId())) continue;
                List<String> keys = new ArrayList<>(out.getOrDefault(id, new ArrayList<>()));
                JsonNode intentKeys = def.path("intentKeys");
                if (intentKeys.isArray()) {
                    for (JsonNode k : intentKeys) {
                        String s = k.asText("").trim();
                        if (!s.isEmpty() && !keys.contains(s)) keys.add(s);
                    }
                }
                out.put(id, keys);
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse subagents_json for agent={}: {}", agent.getAgentKey(), e.getMessage());
        }
        return out;
    }

    private List<UUID> readUuidList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            JsonNode arr = json.readTree(value);
            if (!arr.isArray()) return List.of();
            List<UUID> out = new ArrayList<>();
            for (JsonNode n : arr) out.add(UUID.fromString(n.asText()));
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private JsonNode parseArray(String value) {
        try {
            JsonNode n = json.readTree(value == null || value.isBlank() ? "[]" : value);
            return n.isArray() ? n : json.createArrayNode();
        } catch (Exception e) {
            return json.createArrayNode();
        }
    }

    private JsonNode parseObject(String value) {
        try {
            JsonNode n = json.readTree(value == null || value.isBlank() ? "{}" : value);
            return n.isObject() ? n : json.createObjectNode();
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }

    private static void putDouble(ObjectNode n, String field, Double v) {
        if (v != null) n.put(field, v);
    }

    private static void putInt(ObjectNode n, String field, Integer v) {
        if (v != null) n.put(field, v);
    }

    private static void putNullable(ObjectNode n, String field, String value) {
        if (value == null) n.putNull(field);
        else n.put(field, value);
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
