package io.okagent.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.domain.agent.AgentAsset;
import io.okagent.repository.agent.AgentAssetRepository;
import io.okagent.repository.mcp.McpServerRepository;
import io.okagent.repository.model.ModelAssetRepository;
import io.okagent.repository.skill.SkillAssetRepository;
import io.okagent.web.agent.AgentAssetResponse;
import io.okagent.web.agent.AgentConfigRequest;
import io.okagent.web.agent.AgentCreateRequest;
import io.okagent.web.agent.AgentUpdateRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final ObjectMapper json = new ObjectMapper();

    public AgentAssetServiceImpl(
            AgentAssetRepository agents,
            ModelAssetRepository models,
            McpServerRepository mcpServers,
            SkillAssetRepository skills) {
        this.agents = agents;
        this.models = models;
        this.mcpServers = mcpServers;
        this.skills = skills;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentAssetResponse> list() {
        return agents.findAll().stream()
                .sorted(Comparator.comparing(AgentAsset::getUpdatedAt).reversed())
                .map(AgentAssetResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AgentAssetResponse get(UUID id) {
        return AgentAssetResponse.from(find(id));
    }

    @Override
    @Transactional
    public AgentAssetResponse create(AgentCreateRequest request) {
        var agentKey = slugify(request.name());
        if (agents.existsByAgentKey(agentKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An agent with this name already exists");
        }
        var agent = new AgentAsset(
                UUID.randomUUID(),
                agentKey,
                request.name().trim(),
                text(request.description()),
                request.businessDomain().trim());
        return AgentAssetResponse.from(agents.save(agent));
    }

    @Override
    @Transactional
    public AgentAssetResponse update(UUID id, AgentUpdateRequest request) {
        var agent = find(id);
        agent.updateBasicInfo(
                request.name().trim(),
                text(request.description()),
                request.businessDomain().trim());
        return AgentAssetResponse.from(agents.save(agent));
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
        var saved = agents.save(agent);
        log.info(
                "Agent configuration updated: agentId={} permissionMode={} maxIters={} modelTimeoutSeconds={} toolTimeoutSeconds={}",
                saved.getId(),
                saved.getPermissionMode(),
                saved.getMaxIters(),
                saved.getModelTimeoutSeconds(),
                saved.getToolTimeoutSeconds());
        return AgentAssetResponse.from(saved);
    }

    @Override
    @Transactional
    public AgentAssetResponse setEnabled(UUID id, boolean enabled) {
        var agent = find(id);
        agent.setEnabled(enabled);
        return AgentAssetResponse.from(agents.save(agent));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        agents.delete(find(id));
    }

    private AgentAsset find(UUID id) {
        return agents.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
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

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String slugify(String name) {
        var slug = name.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agent name must contain letters or digits");
        }
        return slug;
    }
}
