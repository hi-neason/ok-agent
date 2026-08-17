package io.okagent.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.transcript.TranscriptStore;
import io.agentscope.harness.agent.filesystem.remote.RemoteFilesystem;
import io.agentscope.harness.agent.filesystem.remote.store.NamespaceFactory;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.agentscope.harness.agent.tools.McpServerConfig;
import io.agentscope.harness.agent.tools.ToolsConfig;
import io.agentscope.harness.agent.workspace.LocalFsMode;
import io.okagent.domain.agent.AgentAsset;
import io.okagent.infrastructure.store.JdbcBaseStore;
import io.okagent.domain.mcp.McpServer;
import io.okagent.domain.model.ModelAsset;
import io.okagent.domain.skill.SkillAsset;
import io.okagent.repository.mcp.McpServerRepository;
import io.okagent.repository.model.ModelAssetRepository;
import io.okagent.repository.skill.SkillAssetRepository;
import io.okagent.service.model.ApiKeyCipher;
import io.okagent.service.persona.UserPersonaService;
import java.nio.file.Path;
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
    private final AgentStateStore stateStore;
    private final TranscriptStore transcriptStore;
    private final JdbcBaseStore baseStore;
    private final UserPersonaService personaService;
    private final ObjectMapper json = new ObjectMapper();

    public HarnessAgentFactory(
            ModelAssetRepository models,
            McpServerRepository mcpServers,
            SkillAssetRepository skills,
            ApiKeyCipher cipher,
            HttpTransport httpTransport,
            AgentStateStore stateStore,
            TranscriptStore transcriptStore,
            JdbcBaseStore baseStore,
            UserPersonaService personaService) {
        this.models = models;
        this.mcpServers = mcpServers;
        this.skills = skills;
        this.cipher = cipher;
        this.httpTransport = httpTransport;
        this.stateStore = stateStore;
        this.transcriptStore = transcriptStore;
        this.baseStore = baseStore;
        this.personaService = personaService;
    }

    public HarnessAgent build(AgentAsset draft) {
        return build(draft, null);
    }

    public HarnessAgent build(AgentAsset draft, String userId) {
        var builder = HarnessAgent.builder()
                .name(safeName(draft.getAgentKey()))
                .description(draft.getDescription() == null ? "" : draft.getDescription())
                .sysPrompt(systemPrompt(draft, userId))
                .maxIters(draft.getMaxIters())
                .modelExecutionConfig(modelExecutionConfig(draft))
                .toolExecutionConfig(toolExecutionConfig(draft))
                .maxContextTokens(draft.getMaxContextTokens())
                .enableAgentTracingLog(draft.isTracingEnabled())
                .stateStore(stateStore)
                .transcriptStore(transcriptStore)
                .disableSubagents()
                // No workspace/tools.json file; register MCP servers programmatically.
                .toolsConfig(toolsConfig(draft));

        configureWorkspace(builder, draft);
        configureMemory(builder, draft);

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
        var toolFilters = readToolFilters(draft.getMcpToolFiltersJson());
        for (McpServer server : mcpServers.findAllById(ids)) {
            var serverConfig = toMcpServerConfig(server);
            var allowlist = toolFilters.getOrDefault(server.getId().toString(), List.of());
            if (!allowlist.isEmpty()) {
                serverConfig.setEnableTools(allowlist);
            }
            servers.put(server.getServerKey(), serverConfig);
        }
        var config = new ToolsConfig();
        config.setMcpServers(servers);
        return config;
    }

    private void configureWorkspace(HarnessAgent.Builder builder, AgentAsset draft) {
        if (draft.getWorkspaceMode() == io.okagent.domain.agent.AgentWorkspaceMode.DISABLED) {
            builder.disableWorkspaceContext().disableFilesystemTools().disableShellTool();
            return;
        }

        var workspace =
                Path.of(System.getProperty("user.dir"), ".agentscope", "workspaces", safeName(draft.getAgentKey()));
        var isolationScope = IsolationScope.valueOf(draft.getWorkspaceIsolationScope());
        builder.workspace(workspace);
        switch (draft.getWorkspaceMode()) {
            case LOCAL_ROOTED -> builder.filesystem(new LocalFilesystemSpec()
                    .mode(LocalFsMode.SANDBOXED)
                    .isolationScope(isolationScope)
                    .inheritEnv(false)
                    .projectWritable(false));
            case DOCKER_SANDBOX -> {
                var spec = new DockerFilesystemSpec()
                        .image(draft.getDockerImage())
                        .memorySizeBytes((long) draft.getSandboxMemoryMb() * 1024 * 1024)
                        .cpuCount((long) draft.getSandboxCpuCount());
                spec.isolationScope(isolationScope);
                builder.filesystem(spec);
            }
            case DISABLED -> throw new IllegalStateException("Disabled workspace handled above");
        }

        // Route the agent's long-term memory (MEMORY.md + memory/YYYY-MM-DD.md) through the
        // MySQL-backed BaseStore so it survives JVM restarts instead of living on local disk.
        // The rest of the workspace (code, shell, etc.) keeps its original backing.
        NamespaceFactory memoryNamespace =
                rc -> List.of("agents", safeName(draft.getAgentKey()), "memory");
        RemoteFilesystem memoryFs = new RemoteFilesystem(baseStore, memoryNamespace);
        builder.filesystemRoute("memory/", memoryFs);
        builder.filesystemRoute("MEMORY.md", memoryFs);

        if (!draft.isWorkspaceContextEnabled()) {
            builder.disableWorkspaceContext();
        }
        if (!draft.isShellEnabled()) {
            builder.disableShellTool();
        }
    }

    private void configureMemory(HarnessAgent.Builder builder, AgentAsset draft) {
        if (!draft.isMemoryEnabled()) {
            builder.disableMemoryTools().disableMemoryHooks();
            return;
        }
        var flushTrigger =
                switch (draft.getMemoryFlushMode()) {
                    case ALWAYS -> MemoryConfig.FlushTrigger.always();
                    case NEVER -> MemoryConfig.FlushTrigger.never();
                    case THROTTLED -> MemoryConfig.FlushTrigger.throttled(
                            Duration.ofMinutes(draft.getMemoryFlushIntervalMinutes()));
                };
        builder.memory(MemoryConfig.builder()
                .flushTrigger(flushTrigger)
                .consolidationMinGap(Duration.ofMinutes(draft.getMemoryConsolidationIntervalMinutes()))
                .dailyFileRetentionDays(draft.getMemoryDailyRetentionDays())
                .sessionRetentionDays(draft.getMemorySessionRetentionDays())
                .build());
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

    private String systemPrompt(AgentAsset draft, String userId) {
        var prompt = draft.getSystemPrompt() == null ? "" : draft.getSystemPrompt().trim();
        var base = prompt.isEmpty() ? "You are a helpful assistant." : prompt;
        if (!draft.isPersonaMemoryEnabled() || userId == null || userId.isBlank()) {
            return base;
        }
        var block = personaService.getProfileBlock(userId, draft.getPersonaPromptTemplate());
        if (block == null || block.isBlank()) {
            return base;
        }
        return base + "\n\n<user_profile>\n" + block.strip() + "\n</user_profile>";
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

    private Map<String, List<String>> readToolFilters(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(value, new TypeReference<Map<String, List<String>>>() {});
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
