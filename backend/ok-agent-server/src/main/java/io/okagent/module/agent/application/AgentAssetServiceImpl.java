package io.okagent.module.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.module.agent.domain.AgentAsset;
import io.okagent.module.mcp.domain.McpToolSnapshot;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.mcp.infrastructure.persistence.McpServerRepository;
import io.okagent.module.mcp.infrastructure.persistence.McpToolSnapshotRepository;
import io.okagent.module.model.infrastructure.persistence.ModelAssetRepository;
import io.okagent.module.skill.infrastructure.persistence.SkillAssetRepository;
import io.okagent.module.channel.application.runtime.AgentConfigChangedEvent;
import io.okagent.module.agent.application.AgentAssetResponse;
import io.okagent.module.agent.application.AgentConfigRequest;
import io.okagent.module.agent.application.AgentConfigValidationCheck;
import io.okagent.module.agent.application.AgentConfigValidationIssue;
import io.okagent.module.agent.application.AgentConfigValidationResponse;
import io.okagent.module.agent.application.AgentCreateRequest;
import io.okagent.module.agent.application.AgentUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentAssetServiceImpl implements AgentAssetService {
    private static final Logger log = LoggerFactory.getLogger(AgentAssetServiceImpl.class);

    private final AgentAssetRepository agents;
    private final ModelAssetRepository models;
    private final McpServerRepository mcpServers;
    private final SkillAssetRepository skills;
    private final McpToolSnapshotRepository mcpToolSnapshots;
    private final Validator validator;
    private final ApplicationEventPublisher events;
    private final ObjectMapper json;

    public AgentAssetServiceImpl(
            AgentAssetRepository agents,
            ModelAssetRepository models,
            McpServerRepository mcpServers,
            SkillAssetRepository skills,
            McpToolSnapshotRepository mcpToolSnapshots,
            Validator validator,
            ApplicationEventPublisher events,
            ObjectMapper json) {
        this.agents = agents;
        this.models = models;
        this.mcpServers = mcpServers;
        this.skills = skills;
        this.mcpToolSnapshots = mcpToolSnapshots;
        this.validator = validator;
        this.events = events;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AgentAssetResponse> list(int page, int size) {
        var modelNames = modelNames();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return agents.findAll(pageable).map(a -> AgentAssetResponse.from(a, modelNames));
    }

    @Override
    @Transactional(readOnly = true)
    public AgentAssetResponse get(UUID id) {
        var agent = find(id);
        return AgentAssetResponse.from(agent, modelNameOf(agent.getModelAssetId()));
    }

    @Override
    @Transactional
    public AgentAssetResponse create(AgentCreateRequest request) {
        var agentKey = UUID.randomUUID().toString();
        var agent = new AgentAsset(
                UUID.randomUUID(),
                agentKey,
                request.name().trim(),
                text(request.description()),
                request.businessDomain().trim());
        agent.setUpdatedBy("system");
        return AgentAssetResponse.from(agents.save(agent), Map.of());
    }

    @Override
    @Transactional
    public AgentAssetResponse update(UUID id, AgentUpdateRequest request) {
        var agent = find(id);
        agent.updateBasicInfo(
                request.name().trim(),
                text(request.description()),
                request.businessDomain().trim());
        agent.setUpdatedBy("system");
        return AgentAssetResponse.from(agents.save(agent), modelNameOf(agent.getModelAssetId()));
    }

    @Override
    @Transactional
    public AgentAssetResponse updateConfiguration(UUID id, AgentConfigRequest request) {
        var agent = find(id);
        validateReferences(request);
        agent.updateConfiguration(
                text(request.systemPrompt()),
                text(request.welcomeMessage()),
                request.modelAssetId(),
                request.temperature(),
                request.topP(),
                request.topK(),
                request.maxTokens(),
                writeUuidList(request.mcpServerIds()),
                writeUuidList(request.skillIds()));
        agent.updateRuntimePolicy(
                request.maxIters(),
                request.modelTimeoutSeconds(),
                request.toolTimeoutSeconds(),
                request.maxRetries(),
                request.permissionMode(),
                request.parallelToolCalls(),
                request.compactionEnabled(),
                request.maxContextTokens(),
                request.toolResultEvictionEnabled(),
                request.tracingEnabled());
        validateCapabilities(request);
        agent.updateCapabilities(
                writeToolFilters(request.mcpToolFilters()),
                request.memoryEnabled(),
                request.memoryFlushMode(),
                request.memoryFlushIntervalMinutes(),
                request.memoryConsolidationIntervalMinutes(),
                request.memoryDailyRetentionDays(),
                request.memorySessionRetentionDays(),
                request.workspaceMode(),
                request.workspaceIsolationScope(),
                request.workspaceContextEnabled(),
                request.shellEnabled(),
                text(request.dockerImage()),
                request.sandboxMemoryMb(),
                request.sandboxCpuCount());
        agent.applyPersonaConfig(
                request.personaExtractEnabled(), request.personaInjectionMode(), text(request.personaPromptTemplate()));
        agent.updateSubagents(request.subagentsJson());
        agent.setUpdatedBy("system");
        var saved = agents.save(agent);
        log.info(
                "Agent configuration updated: agentId={} permissionMode={} maxIters={} modelTimeoutSeconds={} toolTimeoutSeconds={}",
                saved.getId(),
                saved.getPermissionMode(),
                saved.getMaxIters(),
                saved.getModelTimeoutSeconds(),
                saved.getToolTimeoutSeconds());
        events.publishEvent(new AgentConfigChangedEvent(saved.getId()));
        return AgentAssetResponse.from(saved, modelNameOf(saved.getModelAssetId()));
    }

    @Override
    @Transactional
    public AgentAssetResponse setEnabled(UUID id, boolean enabled) {
        var agent = find(id);
        agent.setEnabled(enabled);
        agent.setUpdatedBy("system");
        return AgentAssetResponse.from(agents.save(agent), modelNameOf(agent.getModelAssetId()));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        agents.delete(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public AgentConfigValidationResponse validateConfiguration(UUID id, AgentConfigRequest request) {
        long start = System.nanoTime();
        var report = new ValidationReport();
        collectBeanValidationIssues(request, report);
        var agent = agents.findById(id).orElse(null);
        if (agent == null) {
            report.error("agent", "AGENT_NOT_FOUND", "Agent not found", "core");
            return report.toResponse(start);
        }
        collectModelIssues(request, report);
        collectMcpServerIssues(request, report);
        collectMcpToolFilterIssues(request, report);
        collectSkillIssues(request, report);
        collectCapabilityIssues(request, report);
        collectRuntimeIssues(request, report);
        return report.toResponse(start);
    }

    private void collectBeanValidationIssues(AgentConfigRequest request, ValidationReport report) {
        for (ConstraintViolation<AgentConfigRequest> v : validator.validate(request)) {
            var field = v.getPropertyPath().toString();
            report.error(
                    field,
                    v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                    v.getMessage(),
                    tabForField(field));
        }
    }

    private static String tabForField(String field) {
        return switch (field) {
            case "systemPrompt", "welcomeMessage", "modelAssetId", "temperature", "topP", "topK", "maxTokens" -> "core";
            case "mcpServerIds", "mcpToolFilters" -> "mcp";
            case "skillIds" -> "skills";
            case "memoryEnabled",
                    "memoryFlushMode",
                    "memoryFlushIntervalMinutes",
                    "memoryConsolidationIntervalMinutes",
                    "memoryDailyRetentionDays",
                    "memorySessionRetentionDays" -> "memory";
            case "workspaceMode",
                    "workspaceIsolationScope",
                    "workspaceContextEnabled",
                    "shellEnabled",
                    "dockerImage",
                    "sandboxMemoryMb",
                    "sandboxCpuCount" -> "workspace";
            default -> "runtime";
        };
    }

    private static final Set<String> BUILTIN_TOOL_NAMES = Set.of(
            "read_file",
            "write_file",
            "edit_file",
            "list_directory",
            "search_files",
            "execute_command",
            "run_shell",
            "fetch_url",
            "ask_user");

    private void collectModelIssues(AgentConfigRequest request, ValidationReport report) {
        if (request.modelAssetId() == null) {
            report.warn(
                    "modelAssetId",
                    "MODEL_NOT_SELECTED",
                    "No model is selected; the agent cannot run without a model",
                    "core");
            report.check("model.resolvable", false, "No model selected");
            return;
        }
        var model = models.findById(request.modelAssetId());
        if (model.isEmpty()) {
            report.error("modelAssetId", "MODEL_NOT_FOUND", "Referenced model asset does not exist", "core");
            report.check("model.resolvable", false, "Model not found");
            return;
        }
        var m = model.get();
        if (!m.isEnabled()) {
            report.error("modelAssetId", "MODEL_DISABLED", "Referenced model asset is disabled", "core");
            report.check("model.resolvable", false, "Model disabled");
            return;
        }
        report.check("model.resolvable", true, "Model exists and is enabled");
        if (m.getApiKeyCiphertext() == null || m.getApiKeyCiphertext().isBlank()) {
            report.warn(
                    "modelAssetId",
                    "MODEL_API_KEY_MISSING",
                    "Model has no API key configured; connection tests will fail",
                    "core");
        }
    }

    private void collectMcpServerIssues(AgentConfigRequest request, ValidationReport report) {
        boolean allResolved = true;
        for (UUID mcpId : safeList(request.mcpServerIds())) {
            var server = mcpServers.findById(mcpId);
            if (server.isEmpty()) {
                report.error(
                        "mcpServerIds",
                        "MCP_SERVER_NOT_FOUND",
                        "Referenced MCP server does not exist: " + mcpId,
                        "mcp");
                allResolved = false;
            } else if (!server.get().isEnabled()) {
                report.error(
                        "mcpServerIds",
                        "MCP_SERVER_DISABLED",
                        "Referenced MCP server is disabled: " + server.get().getName(),
                        "mcp");
                allResolved = false;
            }
        }
        report.check(
                "mcp.servers.resolved",
                allResolved,
                allResolved ? "All bound MCP servers resolved" : "One or more MCP servers unresolved");
    }

    private void collectMcpToolFilterIssues(AgentConfigRequest request, ValidationReport report) {
        Set<UUID> bound = Set.copyOf(safeList(request.mcpServerIds()));
        Map<String, List<String>> filters = safeMap(request.mcpToolFilters());
        Set<String> seenToolNames = new HashSet<>();
        boolean allResolved = true;
        for (var entry : filters.entrySet()) {
            String serverId = entry.getKey();
            UUID sid;
            try {
                sid = UUID.fromString(serverId);
            } catch (IllegalArgumentException e) {
                report.error(
                        "mcpToolFilters",
                        "MCP_FILTER_INVALID_SERVER",
                        "Invalid MCP server id in tool filter: " + serverId,
                        "mcp");
                allResolved = false;
                continue;
            }
            if (!bound.contains(sid)) {
                report.error(
                        "mcpToolFilters",
                        "MCP_FILTER_UNBOUND_SERVER",
                        "MCP tool filter references an unbound server",
                        "mcp");
                allResolved = false;
                continue;
            }
            var snapshots = mcpToolSnapshots.findByServerIdOrderByName(sid);
            Set<String> discovered =
                    snapshots.stream().map(McpToolSnapshot::getName).collect(Collectors.toSet());
            if (discovered.isEmpty()) {
                report.warn(
                        "mcpToolFilters",
                        "MCP_TOOLS_NOT_DISCOVERED",
                        "No tools discovered for this MCP server; run tool discovery before restricting the allowlist",
                        "mcp");
            } else {
                for (String tool : entry.getValue()) {
                    if (!discovered.contains(tool)) {
                        report.warn(
                                "mcpToolFilters",
                                "MCP_TOOL_NOT_DISCOVERED",
                                "Allowlisted tool '" + tool + "' was not found in the latest tool snapshot",
                                "mcp");
                    }
                    if (!seenToolNames.add(tool)) {
                        report.warn(
                                "mcpToolFilters",
                                "TOOL_NAME_DUPLICATE",
                                "Tool name '" + tool + "' is allowlisted for more than one server",
                                "mcp");
                    }
                    if (BUILTIN_TOOL_NAMES.contains(tool.toLowerCase(Locale.ROOT))) {
                        report.warn(
                                "mcpToolFilters",
                                "TOOL_NAME_BUILTIN_CONFLICT",
                                "Tool name '" + tool + "' collides with a built-in platform tool",
                                "mcp");
                    }
                }
            }
        }
        report.check(
                "mcp.tools.resolvable",
                allResolved,
                allResolved ? "All tool filters resolve to bound servers" : "One or more tool filters are invalid");
    }

    private void collectSkillIssues(AgentConfigRequest request, ValidationReport report) {
        boolean allResolved = true;
        for (UUID skillId : safeList(request.skillIds())) {
            var skill = skills.findById(skillId);
            if (skill.isEmpty()) {
                report.error("skillIds", "SKILL_NOT_FOUND", "Referenced skill does not exist: " + skillId, "skills");
                allResolved = false;
            } else if (!skill.get().isEnabled()) {
                report.error(
                        "skillIds",
                        "SKILL_DISABLED",
                        "Referenced skill is disabled: " + skill.get().getName(),
                        "skills");
                allResolved = false;
            }
        }
        report.check(
                "skills.resolved",
                allResolved,
                allResolved ? "All bound skills resolved" : "One or more skills unresolved");
    }

    private void collectCapabilityIssues(AgentConfigRequest request, ValidationReport report) {
        if (request.workspaceMode() == io.okagent.module.agent.domain.AgentWorkspaceMode.DOCKER_SANDBOX
                && text(request.dockerImage()).isBlank()) {
            report.error(
                    "dockerImage",
                    "DOCKER_IMAGE_REQUIRED",
                    "Docker image is required for Docker sandbox mode",
                    "workspace");
        }
        if (!Set.of("SESSION", "USER", "AGENT", "GLOBAL").contains(request.workspaceIsolationScope())) {
            report.error(
                    "workspaceIsolationScope",
                    "ISOLATION_SCOPE_INVALID",
                    "Unsupported workspace isolation scope",
                    "workspace");
        } else if ("GLOBAL".equals(request.workspaceIsolationScope())) {
            report.warn(
                    "workspaceIsolationScope",
                    "ISOLATION_SCOPE_GLOBAL_RISK",
                    "GLOBAL isolation scope shares memory/workspace across all agents; review before enabling",
                    "workspace");
        }
        report.check(
                "capabilities.consistent",
                !report.hasError("DOCKER_IMAGE_REQUIRED") && !report.hasError("ISOLATION_SCOPE_INVALID"),
                "Workspace/docker/isolation constraints satisfied");
    }

    private void collectRuntimeIssues(AgentConfigRequest request, ValidationReport report) {
        boolean consistent = true;
        if (request.shellEnabled() && request.permissionMode() != io.okagent.module.agent.domain.AgentPermissionMode.BYPASS) {
            report.warn(
                    "permissionMode",
                    "SHELL_PERMISSION_CONFLICT",
                    "Shell tool is enabled under a restrictive permission mode; the agent may be blocked at runtime",
                    "runtime");
        }
        if (request.maxTokens() != null && request.maxTokens() > request.maxContextTokens()) {
            report.error(
                    "maxContextTokens",
                    "CONTEXT_BUDGET_INVALID",
                    "maxTokens exceeds the maxContextTokens budget",
                    "runtime");
            consistent = false;
        }
        if (request.toolTimeoutSeconds() > request.modelTimeoutSeconds()) {
            report.warn(
                    "toolTimeoutSeconds",
                    "TOOL_TIMEOUT_EXCEEDS_MODEL",
                    "Tool timeout is greater than the model timeout",
                    "runtime");
        }
        report.check(
                "runtime.policy.consistent",
                consistent,
                consistent ? "Runtime policy constraints satisfied" : "Runtime policy has blocking errors");
    }

    private static final class ValidationReport {
        private final List<AgentConfigValidationIssue> errors = new ArrayList<>();
        private final List<AgentConfigValidationIssue> warnings = new ArrayList<>();
        private final List<AgentConfigValidationCheck> checks = new ArrayList<>();

        void error(String field, String code, String message, String tab) {
            errors.add(new AgentConfigValidationIssue(field, code, message, tab));
        }

        void warn(String field, String code, String message, String tab) {
            warnings.add(new AgentConfigValidationIssue(field, code, message, tab));
        }

        void check(String name, boolean passed, String detail) {
            checks.add(new AgentConfigValidationCheck(name, passed, detail));
        }

        boolean hasError(String code) {
            return errors.stream().anyMatch(e -> e.code().equals(code));
        }

        AgentConfigValidationResponse toResponse(long startNanos) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            return new AgentConfigValidationResponse(
                    errors.isEmpty(), List.copyOf(errors), List.copyOf(warnings), List.copyOf(checks), durationMs);
        }
    }

    private AgentAsset find(UUID id) {
        return agents.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
    }

    private Map<UUID, String> modelNames() {
        return models.findAll().stream()
                .collect(Collectors.toMap(
                        io.okagent.module.model.domain.ModelAsset::getId,
                        io.okagent.module.model.domain.ModelAsset::getName,
                        (a, b) -> a));
    }

    private Map<UUID, String> modelNameOf(UUID modelAssetId) {
        if (modelAssetId == null) {
            return Map.of();
        }
        return models.findById(modelAssetId)
                .map(m -> Map.<UUID, String>of(m.getId(), m.getName()))
                .orElseGet(Map::of);
    }

    private void validateReferences(AgentConfigRequest request) {
        if (request.modelAssetId() != null && !models.existsById(request.modelAssetId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Referenced model asset does not exist");
        }
        for (UUID mcpId : safeList(request.mcpServerIds())) {
            if (!mcpServers.existsById(mcpId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Referenced MCP server does not exist: " + mcpId);
            }
        }
        for (UUID skillId : safeList(request.skillIds())) {
            if (!skills.existsById(skillId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Referenced skill does not exist: " + skillId);
            }
        }
    }

    private void validateCapabilities(AgentConfigRequest request) {
        var boundServers = Set.copyOf(safeList(request.mcpServerIds()));
        for (String serverId : safeMap(request.mcpToolFilters()).keySet()) {
            try {
                if (!boundServers.contains(UUID.fromString(serverId))) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "MCP tool filter references an unbound server");
                }
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid MCP server id in tool filter");
            }
        }
        if (request.workspaceMode() == io.okagent.module.agent.domain.AgentWorkspaceMode.DOCKER_SANDBOX
                && text(request.dockerImage()).isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Docker image is required for Docker sandbox mode");
        }
        if (!Set.of("SESSION", "USER", "AGENT", "GLOBAL").contains(request.workspaceIsolationScope())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported workspace isolation scope");
        }
    }

    private List<UUID> safeList(List<UUID> value) {
        return value == null ? List.of() : value;
    }

    private String writeUuidList(List<UUID> value) {
        try {
            return json.writeValueAsString(safeList(value));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize binding list", e);
        }
    }

    private Map<String, List<String>> safeMap(Map<String, List<String>> value) {
        return value == null ? Map.of() : value;
    }

    private String writeToolFilters(Map<String, List<String>> value) {
        try {
            return json.writeValueAsString(safeMap(value));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize MCP tool filters", e);
        }
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
