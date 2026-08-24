package io.okagent.service.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.domain.agent.AgentMemoryFlushMode;
import io.okagent.domain.agent.AgentPermissionMode;
import io.okagent.domain.agent.AgentWorkspaceMode;
import io.okagent.domain.agent.PersonaInjectionMode;
import io.okagent.service.agent.ResolvedAgentConfig;
import io.okagent.service.agent.ResolvedSubagent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A {@link ResolvedAgentConfig} backed by an immutable version snapshot JSON. Production builds
 * consume this so the runtime never reads the editable draft. Child agents referenced by a router
 * are pinned by {@code versionNo} and their snapshots are embedded recursively, so a released
 * version is fully self-contained for construction.
 */
public final class ReleaseAgentConfig implements ResolvedAgentConfig {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JsonNode node;
    private final List<ResolvedSubagent> subagents;
    private final String contentHash;

    private ReleaseAgentConfig(JsonNode node) {
        this.node = node;
        this.subagents = resolveSubagents(node.path("subagents"));
        this.contentHash = text(node, "contentHash", "");
    }

    /** Parses a snapshot JSON document into a config. */
    public static ReleaseAgentConfig fromSnapshot(String snapshotJson) {
        try {
            return new ReleaseAgentConfig(JSON.readTree(snapshotJson));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid version snapshot: " + e.getMessage(), e);
        }
    }

    private static List<ResolvedSubagent> resolveSubagents(JsonNode arr) {
        if (arr == null || !arr.isArray()) return List.of();
        List<ResolvedSubagent> out = new ArrayList<>();
        for (JsonNode item : arr) {
            JsonNode childSnap = item.path("snapshot");
            if (childSnap.isMissingNode() || childSnap.isNull()) continue;
            var child = new ReleaseAgentConfig(childSnap);
            List<String> keys = new ArrayList<>();
            JsonNode intentKeys = item.path("intentKeys");
            if (intentKeys.isArray()) {
                for (JsonNode k : intentKeys) keys.add(k.asText());
            }
            out.add(new ResolvedSubagent(child, keys));
        }
        return List.copyOf(out);
    }

    @Override
    public String contentHash() {
        return contentHash;
    }

    @Override
    public UUID getId() {
        return uuid("agentId");
    }

    @Override
    public String getAgentKey() {
        return text(node, "agentKey", "ok-agent");
    }

    @Override
    public String getName() {
        return text(node, "name", "");
    }

    @Override
    public String getDescription() {
        return text(node, "description", "");
    }

    @Override
    public String getSystemPrompt() {
        return text(node, "systemPrompt", "");
    }

    @Override
    public String getWelcomeMessage() {
        return text(node, "welcomeMessage", "");
    }

    @Override
    public UUID getModelAssetId() {
        return node.hasNonNull("modelAssetId") ? uuid("modelAssetId") : null;
    }

    @Override
    public Double getTemperature() {
        return doubleOrNull("temperature");
    }

    @Override
    public Double getTopP() {
        return doubleOrNull("topP");
    }

    @Override
    public Integer getTopK() {
        return intOrNull("topK");
    }

    @Override
    public Integer getMaxTokens() {
        return intOrNull("maxTokens");
    }

    @Override
    public int getMaxIters() {
        return integer("maxIters", 10);
    }

    @Override
    public int getModelTimeoutSeconds() {
        return integer("modelTimeoutSeconds", 120);
    }

    @Override
    public int getToolTimeoutSeconds() {
        return integer("toolTimeoutSeconds", 60);
    }

    @Override
    public int getMaxRetries() {
        return integer("maxRetries", 2);
    }

    @Override
    public AgentPermissionMode getPermissionMode() {
        return enumValue("permissionMode", AgentPermissionMode.class, AgentPermissionMode.BYPASS);
    }

    @Override
    public boolean isParallelToolCalls() {
        return bool("parallelToolCalls", true);
    }

    @Override
    public boolean isCompactionEnabled() {
        return bool("compactionEnabled", true);
    }

    @Override
    public int getMaxContextTokens() {
        return integer("maxContextTokens", 8000);
    }

    @Override
    public boolean isToolResultEvictionEnabled() {
        return bool("toolResultEvictionEnabled", true);
    }

    @Override
    public boolean isTracingEnabled() {
        return bool("tracingEnabled", true);
    }

    @Override
    public String getMcpServerIdsJson() {
        return jsonArrayField("mcpServerIds");
    }

    @Override
    public String getSkillIdsJson() {
        return jsonArrayField("skillIds");
    }

    @Override
    public String getMcpToolFiltersJson() {
        JsonNode f = node.get("mcpToolFilters");
        if (f == null || f.isNull()) return "{}";
        return f.toString();
    }

    @Override
    public boolean isMemoryEnabled() {
        return bool("memoryEnabled", false);
    }

    @Override
    public AgentMemoryFlushMode getMemoryFlushMode() {
        return enumValue("memoryFlushMode", AgentMemoryFlushMode.class, AgentMemoryFlushMode.THROTTLED);
    }

    @Override
    public int getMemoryFlushIntervalMinutes() {
        return integer("memoryFlushIntervalMinutes", 30);
    }

    @Override
    public int getMemoryConsolidationIntervalMinutes() {
        return integer("memoryConsolidationIntervalMinutes", 30);
    }

    @Override
    public int getMemoryDailyRetentionDays() {
        return integer("memoryDailyRetentionDays", 90);
    }

    @Override
    public int getMemorySessionRetentionDays() {
        return integer("memorySessionRetentionDays", 180);
    }

    @Override
    public boolean isPersonaExtractEnabled() {
        return bool("personaExtractEnabled", false);
    }

    @Override
    public PersonaInjectionMode getPersonaInjectionMode() {
        return enumValue("personaInjectionMode", PersonaInjectionMode.class, PersonaInjectionMode.NONE);
    }

    @Override
    public String getPersonaPromptTemplate() {
        return text(node, "personaPromptTemplate", null);
    }

    @Override
    public AgentWorkspaceMode getWorkspaceMode() {
        return enumValue("workspaceMode", AgentWorkspaceMode.class, AgentWorkspaceMode.DISABLED);
    }

    @Override
    public String getWorkspaceIsolationScope() {
        return text(node, "workspaceIsolationScope", "SESSION");
    }

    @Override
    public boolean isWorkspaceContextEnabled() {
        return bool("workspaceContextEnabled", true);
    }

    @Override
    public boolean isShellEnabled() {
        return bool("shellEnabled", false);
    }

    @Override
    public String getDockerImage() {
        return text(node, "dockerImage", "");
    }

    @Override
    public int getSandboxMemoryMb() {
        return integer("sandboxMemoryMb", 512);
    }

    @Override
    public int getSandboxCpuCount() {
        return integer("sandboxCpuCount", 1);
    }

    @Override
    public List<ResolvedSubagent> getSubagents() {
        return subagents;
    }

    private UUID uuid(String field) {
        return UUID.fromString(node.path(field).asText());
    }

    private Integer intOrNull(String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }

    private Double doubleOrNull(String field) {
        return node.hasNonNull(field) ? node.get(field).asDouble() : null;
    }

    private int integer(String field, int dflt) {
        return node.hasNonNull(field) ? node.get(field).asInt() : dflt;
    }

    private boolean bool(String field, boolean dflt) {
        return node.hasNonNull(field) ? node.get(field).asBoolean() : dflt;
    }

    private String jsonArrayField(String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return "[]";
        return v.toString();
    }

    private <E extends Enum<E>> E enumValue(String field, Class<E> type, E dflt) {
        String raw = text(node, field, null);
        if (raw == null || raw.isBlank()) return dflt;
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            return dflt;
        }
    }

    private static String text(JsonNode node, String field, String dflt) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return dflt;
        return v.asText();
    }
}
