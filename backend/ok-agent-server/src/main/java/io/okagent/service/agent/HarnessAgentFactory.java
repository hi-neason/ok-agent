package io.okagent.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.okagent.domain.agent.AgentAsset;
import io.okagent.domain.mcp.McpServer;
import io.okagent.domain.model.ModelAsset;
import io.okagent.domain.skill.SkillAsset;
import io.okagent.repository.mcp.McpServerRepository;
import io.okagent.repository.model.ModelAssetRepository;
import io.okagent.repository.skill.SkillAssetRepository;
import io.okagent.service.model.ApiKeyCipher;
import java.net.http.HttpClient;
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
        // Build our own toolkit so MCP clients use HTTP/1.1. The JDK HttpClient default
        // (HTTP/2) causes intermittent "chunked transfer encoding / EOF" failures against some
        // MCP gateways during tool calls.
        var toolkit = new Toolkit();
        registerMcpClients(toolkit, draft);

        var builder = HarnessAgent.builder()
                .name(safeName(draft.getAgentKey()))
                .description(draft.getDescription() == null ? "" : draft.getDescription())
                .sysPrompt(systemPrompt(draft))
                .stateStore(new InMemoryAgentStateStore())
                .toolkit(toolkit)
                // The debug runtime is a transient server-side sandbox; skip the
                // workspace/memory/filesystem middlewares so a chat only depends on the
                // configured model, prompt, MCP tools, and skills.
                .disableWorkspaceContext()
                .disableMemoryTools()
                .disableMemoryHooks()
                .disableFilesystemTools()
                .disableSubagents()
                .disableCompaction()
                .disableToolResultEviction()
                // MCP clients are registered directly on the toolkit; don't let the harness
                // re-read workspace/tools.json and double-register with the default transport.
                .disableToolsConfig();

        resolveModel(draft).ifPresent(model -> builder.model(model));

        var skillRepo = skillRepository(draft);
        if (skillRepo != null) {
            builder.skillRepository(skillRepo);
        } else {
            builder.disableDefaultWorkspaceSkills().disableDynamicSkills();
        }

        return builder.build();
    }

    /** Builds MCP clients with HTTP/1.1 forced and registers them on the toolkit. */
    private void registerMcpClients(Toolkit toolkit, AgentAsset draft) {
        var ids = readUuidList(draft.getMcpServerIdsJson());
        if (ids.isEmpty()) {
            return;
        }
        for (McpServer server : mcpServers.findAllById(ids)) {
            try {
                var wrapper = buildMcpClient(server);
                toolkit.registration().mcpClient(wrapper).apply();
                log.info(
                        "Registered MCP server '{}' for agent debug session (transport={})",
                        server.getServerKey(),
                        server.getTransport());
            } catch (Exception e) {
                log.warn("Failed to register MCP server '{}' for agent: {}", server.getServerKey(), e.getMessage());
            }
        }
    }

    private io.agentscope.core.tool.mcp.McpClientWrapper buildMcpClient(McpServer server) {
        var clientBuilder = McpClientBuilder.create(server.getServerKey())
                .timeout(Duration.ofSeconds(Math.max(1, server.getRequestTimeoutSeconds())))
                .initializationTimeout(Duration.ofSeconds(Math.max(1, server.getInitializationTimeoutSeconds())));

        var secrets = readSecrets(server);
        switch (server.getTransport()) {
            case STDIO -> {
                var args = readStringList(server.getArgumentsJson());
                @SuppressWarnings("unchecked")
                var env = (Map<String, String>) secrets.getOrDefault("environment", Map.of());
                clientBuilder.stdioTransport(required(server.getCommand(), "command"), args, env);
            }
            case SSE -> {
                clientBuilder
                        .sseTransport(required(server.getServerUrl(), "serverUrl"))
                        .customizeSseClient(b -> b.version(HttpClient.Version.HTTP_1_1));
                @SuppressWarnings("unchecked")
                var headers = (Map<String, String>) secrets.getOrDefault("headers", Map.of());
                if (!headers.isEmpty()) {
                    clientBuilder.headers(headers);
                }
                var params = readStringMap(server.getQueryParametersJson());
                if (!params.isEmpty()) {
                    clientBuilder.queryParams(params);
                }
            }
            case STREAMABLE_HTTP -> {
                clientBuilder
                        .streamableHttpTransport(required(server.getServerUrl(), "serverUrl"))
                        .customizeStreamableHttpClient(b -> b.version(HttpClient.Version.HTTP_1_1));
                @SuppressWarnings("unchecked")
                var headers = (Map<String, String>) secrets.getOrDefault("headers", Map.of());
                if (!headers.isEmpty()) {
                    clientBuilder.headers(headers);
                }
                var params = readStringMap(server.getQueryParametersJson());
                if (!params.isEmpty()) {
                    clientBuilder.queryParams(params);
                }
            }
        }
        return clientBuilder.buildAsync().block();
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required for MCP transport");
        }
        return value;
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
