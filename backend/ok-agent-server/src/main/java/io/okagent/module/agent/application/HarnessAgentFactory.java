package io.okagent.module.agent.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.CompositeFilesystem;
import io.agentscope.harness.agent.filesystem.remote.RemoteFilesystem;
import io.agentscope.harness.agent.filesystem.remote.store.NamespaceFactory;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.tools.McpServerConfig;
import io.agentscope.harness.agent.tools.ToolsConfig;
import io.agentscope.harness.agent.workspace.LocalFsMode;
import io.okagent.module.agent.domain.AgentAsset;
import io.okagent.module.mcp.domain.McpServer;
import io.okagent.module.model.domain.ModelAsset;
import io.okagent.module.skill.domain.SkillAsset;
import io.okagent.infrastructure.store.JdbcBaseStore;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.mcp.infrastructure.persistence.McpServerRepository;
import io.okagent.module.model.infrastructure.persistence.ModelAssetRepository;
import io.okagent.module.skill.infrastructure.persistence.SkillAssetRepository;
import io.okagent.module.intent.application.IntentDto;
import io.okagent.module.intent.application.IntentNode;
import io.okagent.module.intent.application.IntentService;
import io.okagent.module.knowledge.application.KnowledgeRuntimeCatalog;
import io.okagent.module.knowledge.application.KnowledgeTools;
import io.okagent.module.model.application.ApiKeyCipher;
import io.okagent.module.observe.application.TraceCollectingMiddleware;
import io.okagent.module.persona.application.UserPersonaService;
import io.okagent.module.product.application.ProductRuntimeCatalog;
import io.okagent.module.product.application.ProductTools;
import io.okagent.module.product.application.SolutionRuntimeCatalog;
import io.okagent.module.workflow.application.WorkflowRuntimeCatalog;
import io.okagent.module.workflow.application.WorkflowTools;
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

/**
 * Builds a throwaway {@link HarnessAgent} from a {@link ResolvedAgentConfig}. The same builder
 * serves two callers: the debug runtime passes a {@link DraftAgentConfig} (editable draft, so
 * changes are reflected immediately), and production passes a release-snapshot config so the
 * runtime never reads a draft and sub-agents run their pinned versions.
 */
@Component
public class HarnessAgentFactory {
    private static final Logger log = LoggerFactory.getLogger(HarnessAgentFactory.class);

    private final ModelAssetRepository models;
    private final McpServerRepository mcpServers;
    private final SkillAssetRepository skills;
    private final AgentAssetRepository agents;
    private final ApiKeyCipher cipher;
    private final HttpTransport httpTransport;
    private final AgentStateStore stateStore;
    private final JdbcBaseStore baseStore;
    private final UserPersonaService personaService;
    private final WorkflowRuntimeCatalog workflowCatalog;
    private final KnowledgeRuntimeCatalog knowledgeCatalog;
    private final TraceCollectingMiddleware traceMiddleware;
    private final IntentService intents;
    private final ProductRuntimeCatalog productCatalog;
    private final SolutionRuntimeCatalog solutionCatalog;
    private final ObjectMapper json = new ObjectMapper();

    public HarnessAgentFactory(
            ModelAssetRepository models,
            McpServerRepository mcpServers,
            SkillAssetRepository skills,
            AgentAssetRepository agents,
            ApiKeyCipher cipher,
            HttpTransport httpTransport,
            AgentStateStore stateStore,
            JdbcBaseStore baseStore,
            UserPersonaService personaService,
            WorkflowRuntimeCatalog workflowCatalog,
            KnowledgeRuntimeCatalog knowledgeCatalog,
            TraceCollectingMiddleware traceMiddleware,
            IntentService intents,
            ProductRuntimeCatalog productCatalog,
            SolutionRuntimeCatalog solutionCatalog) {
        this.models = models;
        this.mcpServers = mcpServers;
        this.skills = skills;
        this.agents = agents;
        this.cipher = cipher;
        this.httpTransport = httpTransport;
        this.stateStore = stateStore;
        this.baseStore = baseStore;
        this.personaService = personaService;
        this.workflowCatalog = workflowCatalog;
        this.knowledgeCatalog = knowledgeCatalog;
        this.traceMiddleware = traceMiddleware;
        this.intents = intents;
        this.productCatalog = productCatalog;
        this.solutionCatalog = solutionCatalog;
    }

    /** Debug entry: builds from the editable draft, resolving child agents from their current drafts. */
    public HarnessAgent build(AgentAsset draft) {
        return build(draftConfig(draft), null, false);
    }

    /** Debug entry: builds from the editable draft with a user context for persona injection. */
    public HarnessAgent build(AgentAsset draft, String userId) {
        return build(draftConfig(draft), userId, false);
    }

    /** Production/debug entry: builds from an already-resolved config (draft or release snapshot). */
    public HarnessAgent build(ResolvedAgentConfig config, String userId) {
        return build(config, userId, false);
    }

    /**
     * Builds a subordinate (leaf) agent from a child config. Forced to a leaf so a delegated child
     * cannot itself spawn and create delegation cycles.
     */
    public HarnessAgent buildSubordinate(ResolvedAgentConfig child, String userId) {
        return build(child, userId, true);
    }

    /** Wraps a draft and its currently-referenced child drafts into a config for the debug runtime. */
    public DraftAgentConfig draftConfig(AgentAsset draft) {
        List<ResolvedSubagent> children = new ArrayList<>();
        for (var entry : parseSubagentRefs(draft).entrySet()) {
            agents.findById(entry.getKey())
                    .filter(AgentAsset::isEnabled)
                    .ifPresent(child -> children.add(new ResolvedSubagent(draftConfig(child), entry.getValue())));
        }
        return new DraftAgentConfig(draft, children);
    }

    private HarnessAgent build(ResolvedAgentConfig cfg, String userId, boolean asLeaf) {
        var builder = HarnessAgent.builder()
                .name(safeName(cfg.getAgentKey()))
                .description(cfg.getDescription() == null ? "" : cfg.getDescription())
                .sysPrompt(systemPrompt(cfg, userId))
                .maxIters(cfg.getMaxIters())
                .modelExecutionConfig(modelExecutionConfig(cfg))
                .toolExecutionConfig(toolExecutionConfig(cfg))
                .maxContextTokens(cfg.getMaxContextTokens())
                .enableAgentTracingLog(cfg.isTracingEnabled())
                .stateStore(stateStore)
                .toolsConfig(toolsConfig(cfg))
                .middleware(traceMiddleware);
        if (asLeaf) {
            builder.disableSubagents();
        } else {
            applySubagents(builder, cfg, userId);
        }

        Toolkit toolkit = new Toolkit();
        if (cfg.getId() != null && !workflowCatalog.listForAgent(cfg.getId()).isEmpty()) {
            toolkit.registerTool(new WorkflowTools(workflowCatalog, cfg.getId()));
        }
        if (cfg.getId() != null && !knowledgeCatalog.listForAgent(cfg.getId()).isEmpty()) {
            toolkit.registerTool(new KnowledgeTools(knowledgeCatalog, cfg.getId()));
        }
        if (cfg.getId() != null && productCatalog.hasProducts(cfg.getId())) {
            toolkit.registerTool(new ProductTools(
                    productCatalog,
                    solutionCatalog,
                    cfg.getId(),
                    productCatalog.capabilities(cfg.getId())));
        }
        builder.toolkit(toolkit);

        configureWorkspace(builder, cfg);
        configureMemory(builder);

        if (!cfg.isCompactionEnabled()) {
            builder.disableCompaction();
        }
        if (!cfg.isToolResultEvictionEnabled()) {
            builder.disableToolResultEviction();
        }

        resolveModel(cfg).ifPresent(builder::model);

        var skillRepo = skillRepository(cfg);
        if (skillRepo != null) {
            builder.skillRepository(skillRepo);
        } else {
            builder.disableDefaultWorkspaceSkills().disableDynamicSkills();
        }

        return builder.build();
    }

    /**
     * Registers each resolved sub-agent with both a {@link SubagentDeclaration} (name + enriched
     * description so the router LLM knows when to delegate) and a custom factory that builds the
     * child from its OWN resolved config — for a release that is the pinned child snapshot, never
     * the child's current draft. Built children are forced to a leaf to prevent cycles.
     */
    private void applySubagents(HarnessAgent.Builder builder, ResolvedAgentConfig cfg, String userId) {
        List<ResolvedSubagent> refs = cfg.getSubagents();
        if (refs == null || refs.isEmpty()) {
            builder.disableSubagents();
            return;
        }
        Map<String, IntentDto> intentByKey = new LinkedHashMap<>();
        try {
            for (IntentNode node : intents.getTree()) {
                collectIntents(node, intentByKey);
            }
        } catch (Exception e) {
            log.warn("Failed to load intent tree for sub-agent descriptions: {}", e.getMessage());
        }
        List<SubagentDeclaration> declarations = new ArrayList<>();
        for (ResolvedSubagent ref : refs) {
            ResolvedAgentConfig child = ref.config();
            String name = safeName(child.getAgentKey());
            String desc = buildSubagentDescription(child, ref.intentKeys(), intentByKey);
            declarations.add(SubagentDeclaration.builder()
                    .name(name)
                    .description(desc)
                    .mode(SubagentDeclaration.Mode.SUBAGENT)
                    .build());
            builder.subagentFactory(name, ignored -> buildSubordinate(child, userId));
        }
        builder.subagents(declarations);
    }

    private static String buildSubagentDescription(
            ResolvedAgentConfig child, List<String> intentKeys, Map<String, IntentDto> intentByKey) {
        StringBuilder sb = new StringBuilder();
        String own = child.getDescription() == null ? "" : child.getDescription().trim();
        if (!own.isEmpty()) {
            sb.append(own);
        }
        if (intentKeys != null && !intentKeys.isEmpty()) {
            if (!own.isEmpty()) sb.append('\n');
            sb.append("负责意图：");
            List<String> parts = new ArrayList<>();
            for (String key : intentKeys) {
                IntentDto it = intentByKey.get(key);
                if (it == null) {
                    parts.add(key);
                    continue;
                }
                StringBuilder part = new StringBuilder();
                part.append(it.name());
                String desc = it.description() == null ? "" : it.description().trim();
                if (!desc.isEmpty()) part.append("（").append(desc).append('）');
                if (it.examples() != null && !it.examples().isEmpty()) {
                    part.append(" 示例：").append(String.join(" / ", it.examples()));
                }
                parts.add(part.toString());
            }
            sb.append(String.join("；", parts));
        }
        return sb.isEmpty() ? child.getName() : sb.toString();
    }

    private static void collectIntents(IntentNode node, Map<String, IntentDto> acc) {
        acc.put(node.node().intentKey(), node.node());
        for (IntentNode child : node.children()) {
            collectIntents(child, acc);
        }
    }

    /** Parses {@code [{"agentId":...,"intentKeys":[...]}]} from a draft, collapsing duplicate ids. */
    private Map<UUID, List<String>> parseSubagentRefs(AgentAsset draft) {
        Map<UUID, List<String>> out = new LinkedHashMap<>();
        String raw = draft.getSubagentsJson();
        if (raw == null || raw.isBlank() || "[]".equals(raw.trim())) {
            return out;
        }
        try {
            List<Map<String, Object>> defs = json.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> def : defs) {
                String idText = asText(def.get("agentId"));
                if (idText.isBlank()) continue;
                try {
                    UUID id = UUID.fromString(idText);
                    if (id.equals(draft.getId())) continue;
                    List<String> keys = new ArrayList<>(out.getOrDefault(id, new ArrayList<>()));
                    for (Object k : asObjectList(def.get("intentKeys"))) {
                        String s = asText(k);
                        if (!s.isBlank() && !keys.contains(s)) keys.add(s);
                    }
                    out.put(id, keys);
                } catch (IllegalArgumentException e) {
                    log.warn("Skipping sub-agent with invalid agentId '{}' for agent={}", idText, draft.getAgentKey());
                }
            }
        } catch (Exception e) {
            log.warn("subagents_json is not a valid JSON array for agent={}", draft.getAgentKey());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asObjectList(Object v) {
        if (v instanceof List<?> list) return (List<Object>) list;
        return List.of();
    }

    private ToolsConfig toolsConfig(ResolvedAgentConfig cfg) {
        if (!cfg.getResolvedMcpServers().isEmpty()) {
            return releasedToolsConfig(cfg);
        }
        var ids = readUuidList(cfg.getMcpServerIdsJson());
        if (ids.isEmpty()) {
            var empty = new ToolsConfig();
            empty.setMcpServers(Map.of());
            return empty;
        }
        Map<String, McpServerConfig> servers = new LinkedHashMap<>();
        var toolFilters = readToolFilters(cfg.getMcpToolFiltersJson());
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

    private ToolsConfig releasedToolsConfig(ResolvedAgentConfig cfg) {
        Map<String, McpServerConfig> servers = new LinkedHashMap<>();
        var toolFilters = readToolFilters(cfg.getMcpToolFiltersJson());
        for (ResolvedMcpServer server : cfg.getResolvedMcpServers()) {
            var serverConfig = toMcpServerConfig(server);
            var allowlist = toolFilters.getOrDefault(server.assetId().toString(), List.of());
            if (!allowlist.isEmpty()) serverConfig.setEnableTools(allowlist);
            servers.put(server.serverKey(), serverConfig);
        }
        var config = new ToolsConfig();
        config.setMcpServers(servers);
        return config;
    }

    private void configureWorkspace(HarnessAgent.Builder builder, ResolvedAgentConfig cfg) {
        if (cfg.getWorkspaceMode() == io.okagent.module.agent.domain.AgentWorkspaceMode.DISABLED) {
            builder.disableWorkspaceContext().disableFilesystemTools().disableShellTool();
            return;
        }

        var workspace =
                Path.of(System.getProperty("user.dir"), ".agentscope", "workspaces", safeName(cfg.getAgentKey()));
        var isolationScope = IsolationScope.valueOf(cfg.getWorkspaceIsolationScope());
        builder.workspace(workspace);

        NamespaceFactory memoryNamespace = rc -> List.of("agents", safeName(cfg.getAgentKey()), "memory");
        RemoteFilesystem memoryFs = new RemoteFilesystem(baseStore, memoryNamespace);

        switch (cfg.getWorkspaceMode()) {
            case LOCAL_ROOTED -> {
                var localSpec = new LocalFilesystemSpec()
                        .mode(LocalFsMode.SANDBOXED)
                        .isolationScope(isolationScope)
                        .inheritEnv(false)
                        .projectWritable(false);
                AbstractFilesystem localFs = localSpec.toFilesystem(workspace, isolationScope.toNamespaceFactory());
                AbstractFilesystem composite =
                        new CompositeFilesystem(localFs, Map.of("memory/", memoryFs, "MEMORY.md", memoryFs));
                builder.abstractFilesystem(composite);
            }
            case DOCKER_SANDBOX -> {
                var spec = new DockerFilesystemSpec()
                        .image(cfg.getDockerImage())
                        .memorySizeBytes((long) cfg.getSandboxMemoryMb() * 1024 * 1024)
                        .cpuCount((long) cfg.getSandboxCpuCount());
                spec.isolationScope(isolationScope);
                builder.filesystem(spec);
            }
            case DISABLED -> throw new IllegalStateException("Disabled workspace handled above");
        }

        if (!cfg.isWorkspaceContextEnabled()) {
            builder.disableWorkspaceContext();
        }
        if (!cfg.isShellEnabled()) {
            builder.disableShellTool();
        }
    }

    private void configureMemory(HarnessAgent.Builder builder) {
        // Harness 自带的长期记忆表面在多用户产品场景下不适用（会二次 LLM 阻塞、按 agentKey 共享造成跨用户
        // 污染）；用户维度记忆由 persona 模块独立承担，因此统一关闭 harness 记忆表面。
        builder.disableMemoryTools().disableMemoryHooks();
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

    private McpServerConfig toMcpServerConfig(ResolvedMcpServer server) {
        var cfg = new McpServerConfig();
        var secretAsset = mcpServers.findById(server.assetId())
                .orElseThrow(() -> new IllegalStateException("MCP secret reference not found: " + server.assetId()));
        var secrets = readSecrets(secretAsset);
        switch (server.transport()) {
            case STDIO -> {
                cfg.setTransport("stdio");
                cfg.setCommand(server.command());
                cfg.setArgs(server.arguments());
                @SuppressWarnings("unchecked")
                var env = (Map<String, String>) secrets.getOrDefault("environment", Map.of());
                if (!env.isEmpty()) cfg.setEnv(env);
            }
            case SSE -> {
                cfg.setTransport("sse");
                cfg.setUrl(server.serverUrl());
                @SuppressWarnings("unchecked")
                var headers = (Map<String, String>) secrets.getOrDefault("headers", Map.of());
                if (!headers.isEmpty()) cfg.setHeaders(headers);
            }
            case STREAMABLE_HTTP -> {
                cfg.setTransport("http");
                cfg.setUrl(server.serverUrl());
                @SuppressWarnings("unchecked")
                var headers = (Map<String, String>) secrets.getOrDefault("headers", Map.of());
                if (!headers.isEmpty()) cfg.setHeaders(headers);
            }
        }
        if (!server.queryParameters().isEmpty()) cfg.setQueryParams(server.queryParameters());
        cfg.setTimeout(Duration.ofSeconds(server.requestTimeoutSeconds()));
        cfg.setInitializationTimeout(Duration.ofSeconds(server.initializationTimeoutSeconds()));
        return cfg;
    }

    private String systemPrompt(ResolvedAgentConfig cfg, String userId) {
        var prompt = cfg.getSystemPrompt() == null ? "" : cfg.getSystemPrompt().trim();
        var base = prompt.isEmpty() ? "You are a helpful assistant." : prompt;
        var mode = cfg.getPersonaInjectionMode();
        if (mode == null
                || mode == io.okagent.module.agent.domain.PersonaInjectionMode.NONE
                || userId == null
                || userId.isBlank()
                || cfg.getId() == null) {
            return base;
        }
        var block =
                personaService.getProfileBlock(userId, cfg.getId(), mode.name(), cfg.getPersonaPromptTemplate());
        if (block == null || block.isBlank()) {
            return base;
        }
        return base + "\n\n<user_profile>\n" + block.strip() + "\n</user_profile>";
    }

    private java.util.Optional<OpenAIChatModel> resolveModel(ResolvedAgentConfig cfg) {
        if (cfg.getModelAssetId() == null) {
            return java.util.Optional.empty();
        }
        if (cfg.getResolvedModelAsset() != null) {
            ResolvedModelAsset model = cfg.getResolvedModelAsset();
            var secretAsset = models.findById(model.assetId())
                    .orElseThrow(() -> new IllegalStateException("Model secret reference not found: " + model.assetId()));
            return java.util.Optional.of(buildModel(cfg, model.modelId(), model.endpoint(), secretAsset));
        }
        return models.findById(cfg.getModelAssetId())
                .filter(ModelAsset::isEnabled)
                .map(model -> buildModel(cfg, model.getModelId(), model.getEndpoint(), model));
    }

    private OpenAIChatModel buildModel(
            ResolvedAgentConfig cfg, String modelId, String endpoint, ModelAsset secretAsset) {
        var options = GenerateOptions.builder()
                .temperature(cfg.getTemperature())
                .topP(cfg.getTopP())
                .topK(cfg.getTopK())
                .maxTokens(cfg.getMaxTokens())
                .parallelToolCalls(cfg.isParallelToolCalls())
                .build();
        return OpenAIChatModel.builder()
                .apiKey(cipher.decrypt(secretAsset.getApiKeyCiphertext()))
                .baseUrl(endpoint)
                .modelName(modelId)
                .httpTransport(httpTransport)
                .stream(false)
                .generateOptions(options)
                .build();
    }

    private ExecutionConfig modelExecutionConfig(ResolvedAgentConfig cfg) {
        return ExecutionConfig.builder()
                .timeout(Duration.ofSeconds(cfg.getModelTimeoutSeconds()))
                .maxAttempts(cfg.getMaxRetries() + 1)
                .initialBackoff(Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(30))
                .backoffMultiplier(2.0)
                .retryOn(ExecutionConfig.RETRYABLE_ERRORS)
                .build();
    }

    private ExecutionConfig toolExecutionConfig(ResolvedAgentConfig cfg) {
        return ExecutionConfig.builder()
                .timeout(Duration.ofSeconds(cfg.getToolTimeoutSeconds()))
                .maxAttempts(1)
                .build();
    }

    private AgentSkillRepository skillRepository(ResolvedAgentConfig cfg) {
        if (!cfg.getResolvedSkillAssets().isEmpty()) {
            List<AgentSkill> bound = cfg.getResolvedSkillAssets().stream()
                    .filter(skill -> skill.content() != null && !skill.content().isBlank())
                    .map(skill -> new AgentSkill(
                            Map.of("name", skill.skillKey(), "description", skill.description()),
                            skill.content(),
                            Map.of(),
                            "ok-agent-release",
                            null))
                    .toList();
            return bound.isEmpty() ? null : new InMemoryAgentSkillRepository(bound);
        }
        var ids = readUuidList(cfg.getSkillIdsJson());
        if (ids.isEmpty()) {
            return null;
        }
        List<AgentSkill> bound = new ArrayList<>();
        for (SkillAsset skill : skills.findAllById(ids)) {
            if (!skill.isEnabled() || skill.getContent() == null || skill.getContent().isBlank()) {
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
        if (server.getSecretsCiphertext() == null || server.getSecretsCiphertext().isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(
                    cipher.decrypt(server.getSecretsCiphertext()), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String asText(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private String safeName(String agentKey) {
        return agentKey == null || agentKey.isBlank() ? "ok-agent-debug" : agentKey;
    }
}
