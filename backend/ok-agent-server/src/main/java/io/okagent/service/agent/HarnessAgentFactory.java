package io.okagent.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.tools.McpServerConfig;
import io.agentscope.harness.agent.tools.ToolsConfig;
import io.okagent.domain.agent.AgentAsset;
import io.okagent.domain.mcp.McpServer;
import io.okagent.domain.model.ModelAsset;
import io.okagent.domain.skill.SkillAsset;
import io.okagent.repository.mcp.McpServerRepository;
import io.okagent.repository.model.ModelAssetRepository;
import io.okagent.repository.skill.SkillAssetRepository;
import io.okagent.service.model.ApiKeyCipher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Builds a throwaway {@link HarnessAgent} from an {@link AgentAsset} draft for the debug runtime. */
@Component
public class HarnessAgentFactory {
    private static final Logger log = LoggerFactory.getLogger(HarnessAgentFactory.class);

    private final ModelAssetRepository models;
    private final McpServerRepository mcpServers;
    private final SkillAssetRepository skills;
    private final ApiKeyCipher cipher;
    private final HttpTransport httpTransport;
    private final ObjectMapper json = new ObjectMapper();

    public HarnessAgentFactory(
            ModelAssetRepository models,
            McpServerRepository mcpServers,
            SkillAssetRepository skills,
            ApiKeyCipher cipher,
            HttpTransport httpTransport) {
        this.models = models;
        this.mcpServers = mcpServers;
        this.skills = skills;
        this.cipher = cipher;
        this.httpTransport = httpTransport;
    }

    public HarnessAgent build(AgentAsset draft) {
        var builder = HarnessAgent.builder()
                .name(safeName(draft.getAgentKey()))
                .description(draft.getDescription() == null ? "" : draft.getDescription())
                .sysPrompt(systemPrompt(draft))
                .maxIters(draft.getMaxIters())
                .modelExecutionConfig(modelExecutionConfig(draft))
                .toolExecutionConfig(toolExecutionConfig(draft))
                .maxContextTokens(draft.getMaxContextTokens())
                .enableAgentTracingLog(draft.isTracingEnabled())
                .stateStore(new InMemoryAgentStateStore())
                // The debug runtime is a transient server-side sandbox; skip the
                // workspace/memory/filesystem middlewares so a chat only depends on the
                // configured model, prompt, MCP tools, and skills.
                .disableWorkspaceContext()
                .disableMemoryTools()
                .disableMemoryHooks()
                .disableFilesystemTools()
                .disableSubagents()
                // No workspace/tools.json file; register MCP servers programmatically.
                .toolsConfig(toolsConfig(draft));

        if (!draft.isCompactionEnabled()) {
            builder.disableCompaction();
        }
        if (!draft.isToolResultEvictionEnabled()) {
            builder.disableToolResultEviction();
        }

        resolveModel(draft).ifPresent(model -> builder.model(model));

        var skillRepo = skillRepository(draft);
        if (skillRepo != null) {
            builder.skillRepository(skillRepo);
        } else {
            builder.disableDefaultWorkspaceSkills().disableDynamicSkills();
        }

        return builder.build();
    }

    /**
     * Builds the {@link ToolsConfig} for the bound MCP servers. Registered by the harness after it
     * copies the toolkit, so MCP clients survive the copy and are reachable during tool execution.
     */
    private ToolsConfig toolsConfig(AgentAsset draft) {
        var ids = readUuidList(draft.getMcpServerIdsJson());
        if (ids.isEmpty()) {
            var empty = new ToolsConfig();
            empty.setMcpServers(Map.of());
            return empty;
        }
        Map<String, McpServerConfig> servers = new LinkedHashMap<>();
        for (McpServer server : mcpServers.findAllById(ids)) {
            servers.put(server.getServerKey(), toMcpServerConfig(server));
        }
        var config = new ToolsConfig();
        config.setMcpServers(servers);
        return config;
    }

    private McpServerConfig toMcpServerConfig(McpServer server) {
        var cfg = new McpServerConfig();
        var secrets = readSecrets(server);
        switch (server.getTransport()) {
            case STDIO -> {
                cfg.setTransport("stdio");
                cfg.setCommand(server.getCommand());
                cfg.setArgs(readStringList(server.getArgumentsJson()));
                @SuppressWarnings("unchecked")
                var env = (Map<String, String>) secrets.getOrDefault("environment", Map.of());
                if (!env.isEmpty()) {
                    cfg.setEnv(env);
                }
            }
            case SSE -> {
                cfg.setTransport("sse");
                cfg.setUrl(server.getServerUrl());
                @SuppressWarnings("unchecked")
                var headers = (Map<String, String>) secrets.getOrDefault("headers", Map.of());
                if (!headers.isEmpty()) {
                    cfg.setHeaders(headers);
                }
            }
            case STREAMABLE_HTTP -> {
                cfg.setTransport("http");
                cfg.setUrl(server.getServerUrl());
                @SuppressWarnings("unchecked")
                var headers = (Map<String, String>) secrets.getOrDefault("headers", Map.of());
                if (!headers.isEmpty()) {
                    cfg.setHeaders(headers);
                }
            }
        }
        var params = readStringMap(server.getQueryParametersJson());
        if (!params.isEmpty()) {
            cfg.setQueryParams(params);
        }
        if (server.getRequestTimeoutSeconds() > 0) {
            cfg.setTimeout(Duration.ofSeconds(server.getRequestTimeoutSeconds()));
        }
        if (server.getInitializationTimeoutSeconds() > 0) {
            cfg.setInitializationTimeout(Duration.ofSeconds(server.getInitializationTimeoutSeconds()));
        }
        return cfg;
    }

    private String systemPrompt(AgentAsset draft) {
        var prompt =
                draft.getSystemPrompt() == null ? "" : draft.getSystemPrompt().trim();
        return prompt.isEmpty() ? "You are a helpful assistant." : prompt;
    }

    private java.util.Optional<OpenAIChatModel> resolveModel(AgentAsset draft) {
        if (draft.getModelAssetId() == null) {
            return java.util.Optional.empty();
        }
        return models.findById(draft.getModelAssetId())
                .filter(ModelAsset::isEnabled)
                .map(model -> {
                    var options = GenerateOptions.builder()
                            .temperature(draft.getTemperature())
                            .topP(draft.getTopP())
                            .topK(draft.getTopK())
                            .maxTokens(draft.getMaxTokens())
                            .parallelToolCalls(draft.isParallelToolCalls())
                            .build();
                    return OpenAIChatModel.builder()
                            .apiKey(cipher.decrypt(model.getApiKeyCiphertext()))
                            .baseUrl(model.getEndpoint())
                            .modelName(model.getModelId())
                            .httpTransport(httpTransport)
                            .stream(false)
                            .generateOptions(options)
                            .build();
                });
    }

    private ExecutionConfig modelExecutionConfig(AgentAsset draft) {
        return ExecutionConfig.builder()
                .timeout(Duration.ofSeconds(draft.getModelTimeoutSeconds()))
                .maxAttempts(draft.getMaxRetries() + 1)
                .initialBackoff(Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(30))
                .backoffMultiplier(2.0)
                .retryOn(ExecutionConfig.RETRYABLE_ERRORS)
                .build();
    }

    private ExecutionConfig toolExecutionConfig(AgentAsset draft) {
        return ExecutionConfig.builder()
                .timeout(Duration.ofSeconds(draft.getToolTimeoutSeconds()))
                .maxAttempts(1)
                .build();
    }

    private AgentSkillRepository skillRepository(AgentAsset draft) {
        var ids = readUuidList(draft.getSkillIdsJson());
        if (ids.isEmpty()) {
            return null;
        }
        List<AgentSkill> bound = new ArrayList<>();
        for (SkillAsset skill : skills.findAllById(ids)) {
            if (!skill.isEnabled()
                    || skill.getContent() == null
                    || skill.getContent().isBlank()) {
                continue;
            }
            var metadata = new LinkedHashMap<String, Object>();
            metadata.put("name", skill.getSkillKey());
            metadata.put("description", skill.getDescription() == null ? "" : skill.getDescription());
            bound.add(new AgentSkill(metadata, skill.getContent(), Map.of(), "ok-agent", null));
        }
        return bound.isEmpty() ? null : new InMemoryAgentSkillRepository(bound);
    }

    private List<UUID> readUuidList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(value, new TypeReference<List<UUID>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> readStringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(value, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, String> readStringMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(value, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readSecrets(McpServer server) {
        if (server.getSecretsCiphertext() == null
                || server.getSecretsCiphertext().isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(
                    cipher.decrypt(server.getSecretsCiphertext()), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String safeName(String agentKey) {
        return agentKey == null || agentKey.isBlank() ? "ok-agent-debug" : agentKey;
    }
}
