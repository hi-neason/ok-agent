package io.okagent.service.agent;

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
import io.agentscope.harness.agent.transcript.TranscriptStore;
import io.agentscope.harness.agent.filesystem.remote.RemoteFilesystem;
import io.agentscope.harness.agent.filesystem.remote.store.NamespaceFactory;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.agentscope.harness.agent.tools.McpServerConfig;
import io.agentscope.harness.agent.tools.ToolsConfig;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.workspace.LocalFsMode;
import io.okagent.domain.agent.AgentAsset;
import io.okagent.infrastructure.store.JdbcBaseStore;
import io.okagent.domain.mcp.McpServer;
import io.okagent.domain.model.ModelAsset;
import io.okagent.domain.skill.SkillAsset;
import io.okagent.repository.agent.AgentAssetRepository;
import io.okagent.repository.mcp.McpServerRepository;
import io.okagent.repository.model.ModelAssetRepository;
import io.okagent.repository.skill.SkillAssetRepository;
import io.okagent.service.intent.IntentDto;
import io.okagent.service.intent.IntentNode;
import io.okagent.service.intent.IntentService;
import io.okagent.service.model.ApiKeyCipher;
import io.okagent.service.knowledge.KnowledgeRuntimeCatalog;
import io.okagent.service.knowledge.KnowledgeTools;
import io.okagent.service.persona.UserPersonaService;
import io.okagent.service.workflow.WorkflowRuntimeCatalog;
import io.okagent.service.workflow.WorkflowTools;
import io.okagent.service.observe.TraceCollectingMiddleware;
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
    private final AgentAssetRepository agents;
    private final ApiKeyCipher cipher;
    private final HttpTransport httpTransport;
    private final AgentStateStore stateStore;
    private final TranscriptStore transcriptStore;
    private final JdbcBaseStore baseStore;
    private final UserPersonaService personaService;
    private final WorkflowRuntimeCatalog workflowCatalog;
    private final KnowledgeRuntimeCatalog knowledgeCatalog;
    private final TraceCollectingMiddleware traceMiddleware;
    private final IntentService intents;
    private final ObjectMapper json = new ObjectMapper();

    public HarnessAgentFactory(
            ModelAssetRepository models,
            McpServerRepository mcpServers,
            SkillAssetRepository skills,
            AgentAssetRepository agents,
            ApiKeyCipher cipher,
            HttpTransport httpTransport,
            AgentStateStore stateStore,
            TranscriptStore transcriptStore,
            JdbcBaseStore baseStore,
            UserPersonaService personaService,
            WorkflowRuntimeCatalog workflowCatalog,
            KnowledgeRuntimeCatalog knowledgeCatalog,
            TraceCollectingMiddleware traceMiddleware,
            IntentService intents) {
        this.models = models;
        this.mcpServers = mcpServers;
        this.skills = skills;
        this.agents = agents;
        this.cipher = cipher;
        this.httpTransport = httpTransport;
        this.stateStore = stateStore;
        this.transcriptStore = transcriptStore;
        this.baseStore = baseStore;
        this.personaService = personaService;
        this.workflowCatalog = workflowCatalog;
        this.knowledgeCatalog = knowledgeCatalog;
        this.traceMiddleware = traceMiddleware;
        this.intents = intents;
    }

    public HarnessAgent build(AgentAsset draft) {
        return build(draft, null, false);
    }

    public HarnessAgent build(AgentAsset draft, String userId) {
        return build(draft, userId, false);
    }

    /**
     * Builds a subordinate (leaf) agent from an existing {@link AgentAsset} — used as a custom
     * subagent factory so a referenced child agent runs with its OWN model/MCP/skills/systemPrompt
     * rather than inheriting the router's. The result is forced to a leaf ({@code disableSubagents})
     * to prevent a child from further spawning and creating delegation cycles.
     */
    public HarnessAgent buildSubordinate(AgentAsset child, String userId) {
        return build(child, userId, true);
    }

    private HarnessAgent build(AgentAsset draft, String userId, boolean asLeaf) {
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
                // No workspace/tools.json file; register MCP servers programmatically.
                .toolsConfig(toolsConfig(draft))
                // In-process execution tracing: captures agent/model/tool spans (including
                // knowledge-base and workflow tools) and persists them to MySQL.
                .middleware(traceMiddleware);
        if (asLeaf) {
            // A subordinate (referenced child) agent must never itself spawn sub-agents,
            // otherwise delegation can recurse. Force single-agent/leaf behaviour.
            builder.disableSubagents();
        } else {
            // Router (main-sub) topology: when the agent references other agents as sub-agents,
            // register them so the harness exposes agent_spawn/agent_send and the LLM can delegate
            // by name. Otherwise keep the single-agent behaviour. Defensive: a malformed
            // declaration must never break the whole router build, so failures fall back to the
            // single-agent mode.
            applySubagents(builder, draft, userId);
        }

        // Register external tools (workflows + knowledge) when this agent has bindings. The
        // harness copies this toolkit during build() and then appends its own built-in tools
        // (memory/filesystem/shell/web), so none of those are lost.
        Toolkit toolkit = new Toolkit();
        if (draft.getId() != null && !workflowCatalog.listForAgent(draft.getId()).isEmpty()) {
            toolkit.registerTool(new WorkflowTools(workflowCatalog, draft.getId()));
        }
        if (draft.getId() != null && !knowledgeCatalog.listForAgent(draft.getId()).isEmpty()) {
            toolkit.registerTool(new KnowledgeTools(knowledgeCatalog, draft.getId()));
        }
        builder.toolkit(toolkit);

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
     * Enables sub-agents when the draft references other agents, otherwise keeps single-agent mode.
     * A malformed declaration must never break the whole router build, so parsing failures fall
     * back to {@code disableSubagents()}.
     *
     * <p>Each referenced agent is wired with TWO same-named registrations that the harness merges:
     * a {@link SubagentDeclaration} carrying the child's name/description (so the router LLM knows
     * <em>when</em> to delegate) and a custom {@code subagentFactory} that builds the child from
     * <em>its own</em> AgentAsset — its own model, MCP servers, skills, system prompt and workspace
     * — instead of inheriting the router's. The built child is forced to a leaf
     * ({@link #buildSubordinate}) to prevent delegation cycles.
     */
    private void applySubagents(HarnessAgent.Builder builder, AgentAsset draft, String userId) {
        List<SubagentRef> refs;
        try {
            refs = loadReferencedSubagents(draft);
        } catch (Exception e) {
            log.warn("Failed to load referenced sub-agents for agent={}: {}",
                    draft.getAgentKey(), e.getMessage());
            refs = List.of();
        }
        if (refs.isEmpty()) {
            builder.disableSubagents();
            return;
        }
        // Build a lookup of intentKey -> intent metadata so we can enrich each sub-agent's
        // declaration description with the intents it is responsible for.
        Map<String, IntentDto> intentByKey = new LinkedHashMap<>();
        try {
            for (IntentNode node : intents.getTree()) {
                collectIntents(node, intentByKey);
            }
        } catch (Exception e) {
            log.warn("Failed to load intent tree for sub-agent descriptions: {}", e.getMessage());
        }
        // Register each referenced child with TWO same-named registrations that the harness
        // merges: (1) a SubagentDeclaration carrying the enriched description so the router LLM
        // sees "负责意图：…" in the ### Available agent ids list, and (2) a custom subagentFactory
        // that builds the child from its OWN AgentAsset (its own model, MCP, skills, system
        // prompt, workspace) instead of inheriting the router's config via the declaration's
        // default factory. The built child is forced to a leaf (asLeaf=true) to prevent
        // delegation cycles. The dual registration creates a cosmetic duplicate entry in the
        // agent list (the factory entry's description falls back to its name/UUID), but
        // function-calling is driven by the routing directive which names the exact agent_id,
        // so the duplicate does not affect delegation correctness.
        List<SubagentDeclaration> declarations = new ArrayList<>();
        for (SubagentRef ref : refs) {
            AgentAsset child = ref.child();
            String name = safeName(child.getAgentKey());
            String desc = buildSubagentDescription(child, ref.intentKeys(), intentByKey);
            declarations.add(SubagentDeclaration.builder()
                    .name(name)
                    .description(desc)
                    .mode(SubagentDeclaration.Mode.SUBAGENT)
                    .build());
            final AgentAsset capturedChild = child;
            builder.subagentFactory(name, ignored -> buildSubordinate(capturedChild, userId));
        }
        builder.subagents(declarations);
    }

    /**
     * Enriches the child agent's own description with the intents it handles so the router LLM
     * can make an informed delegation decision from the {@code ### Available agent ids} list.
     */
    private static String buildSubagentDescription(
            AgentAsset child, List<String> intentKeys, Map<String, IntentDto> intentByKey) {
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

    /**
     * Parses {@code agent_asset.subagents_json} as a list of {@code {"agentId": ..., "intentKeys":
     * [...]}} references and loads each target AgentAsset. Duplicate agentIds are collapsed;
     * self-references and missing targets are skipped. The returned refs carry each child's
     * declared {@code intentKeys} so the caller can enrich the sub-agent description.
     */
    private List<SubagentRef> loadReferencedSubagents(AgentAsset draft) {
        String raw = draft.getSubagentsJson();
        if (raw == null || raw.isBlank() || "[]".equals(raw.trim())) {
            return List.of();
        }
        List<Map<String, Object>> defs;
        try {
            defs = json.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("subagents_json is not a valid JSON array for agent={}", draft.getAgentKey());
            return List.of();
        }
        // Preserve order but collapse duplicate agentIds (union their intentKeys).
        Map<UUID, List<String>> intentKeysByAgent = new LinkedHashMap<>();
        for (Map<String, Object> def : defs) {
            String idText = asText(def.get("agentId"));
            if (idText.isBlank()) continue;
            try {
                UUID id = UUID.fromString(idText);
                if (id.equals(draft.getId())) continue;
                List<String> keys = new ArrayList<>(
                        intentKeysByAgent.getOrDefault(id, new ArrayList<>()));
                for (Object k : asObjectList(def.get("intentKeys"))) {
                    String s = asText(k);
                    if (!s.isBlank() && !keys.contains(s)) keys.add(s);
                }
                intentKeysByAgent.put(id, keys);
            } catch (IllegalArgumentException e) {
                log.warn("Skipping sub-agent with invalid agentId '{}' for agent={}",
                        idText, draft.getAgentKey());
            }
        }
        if (intentKeysByAgent.isEmpty()) {
            return List.of();
        }
        List<SubagentRef> out = new ArrayList<>();
        for (var a : agents.findAllById(intentKeysByAgent.keySet())) {
            if (a != null && a.isEnabled()) {
                out.add(new SubagentRef(a, intentKeysByAgent.get(a.getId())));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asObjectList(Object v) {
        if (v instanceof List<?> list) return (List<Object>) list;
        return List.of();
    }

    /** A referenced child agent paired with the intentKeys it claims. */
    private record SubagentRef(AgentAsset child, List<String> intentKeys) {}

    private static String asText(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String asText(Object v, String fallback) {
        String s = asText(v);
        return s.isEmpty() ? fallback : s;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object v) {
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
            return out;
        }
        return List.of();
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
        // Harness 自带的长期记忆表面（MEMORY.md 自动 flush + memory_* 工具）在多用户产品场景下
        // 不适用：
        //   1. MemoryFlushMiddleware 用 concatWith 挂在回复流尾部，每轮触发一次二次 LLM 抽取
        //      （实测约 30s），直接 blockLast 阻塞接口返回；
        //   2. memory namespace 仅按 agentKey 隔离（agents/{agentKey}/memory），成千上万用户
        //      共享同一份 MEMORY.md，自动 flush 与 memory_save 都会造成跨用户污染与并发覆盖。
        // 用户维度的长期记忆已由 persona 模块（user_persona 表 + 按 (userId,agentId) 异步抽取 +
        // systemPrompt 注入 <user_profile>）独立承担，因此统一关闭 harness 记忆表面。
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

    private String systemPrompt(AgentAsset draft, String userId) {
        var prompt = draft.getSystemPrompt() == null ? "" : draft.getSystemPrompt().trim();
        var base = prompt.isEmpty() ? "You are a helpful assistant." : prompt;
        var mode = draft.getPersonaInjectionMode();
        if (mode == null || mode == io.okagent.domain.agent.PersonaInjectionMode.NONE
                || userId == null || userId.isBlank() || draft.getId() == null) {
            return base;
        }
        var block = personaService.getProfileBlock(
                userId, draft.getId(), mode.name(), draft.getPersonaPromptTemplate());
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
