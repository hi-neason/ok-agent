package io.okagent.module.workflow.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.module.workflow.domain.AgentWorkflowBinding;
import io.okagent.module.workflow.domain.WorkflowCatalogItem;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.workflow.infrastructure.persistence.AgentWorkflowBindingRepository;
import io.okagent.module.workflow.infrastructure.persistence.WorkflowCatalogItemRepository;
import io.okagent.module.workflow.infrastructure.persistence.WorkflowSourceRepository;
import io.okagent.module.workflow.application.AgentWorkflowBindingRequest;
import io.okagent.module.workflow.application.AgentWorkflowBindingResponse;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentWorkflowBindingServiceImpl implements AgentWorkflowBindingService {
    private final AgentWorkflowBindingRepository bindings;
    private final WorkflowCatalogItemRepository items;
    private final WorkflowSourceRepository sources;
    private final AgentAssetRepository agents;
    private final ObjectMapper json = new ObjectMapper();

    public AgentWorkflowBindingServiceImpl(
            AgentWorkflowBindingRepository bindings,
            WorkflowCatalogItemRepository items,
            WorkflowSourceRepository sources,
            AgentAssetRepository agents) {
        this.bindings = bindings;
        this.items = items;
        this.sources = sources;
        this.agents = agents;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentWorkflowBindingResponse> list(UUID agentId) {
        ensureAgentExists(agentId);
        var byId = new HashMap<UUID, WorkflowCatalogItem>();
        for (var item : items.findAll()) {
            byId.put(item.getId(), item);
        }
        var sourceNames = new HashMap<UUID, String>();
        sources.findAll().forEach(s -> sourceNames.put(s.getId(), s.getName()));
        return bindings.findByAgentId(agentId).stream()
                .sorted(Comparator.comparing(AgentWorkflowBinding::getCreatedAt))
                .map(b -> {
                    var item = byId.get(b.getCatalogItemId());
                    return new AgentWorkflowBindingResponse(
                            b.getId(),
                            b.getAgentId(),
                            b.getCatalogItemId(),
                            item == null ? "" : item.getRemoteWorkflowId(),
                            item == null ? "(missing)" : item.getName(),
                            item == null ? "" : sourceNames.getOrDefault(item.getSourceId(), ""),
                            b.getDescriptionOverride(),
                            b.getParameterDefaultsJson(),
                            b.isEnabled(),
                            item == null ? "MISSING" : item.getMetadataStatus().name(),
                            item != null && item.isActive(),
                            b.getUpdatedAt());
                })
                .toList();
    }

    @Override
    @Transactional
    public List<AgentWorkflowBindingResponse> replace(UUID agentId, List<AgentWorkflowBindingRequest> requests) {
        ensureAgentExists(agentId);
        var valid = requests == null ? List.<AgentWorkflowBindingRequest>of() : requests;
        for (var req : valid) {
            if (req.catalogItemId() != null
                    && items.findById(req.catalogItemId()).isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Workflow catalog item does not exist: " + req.catalogItemId());
            }
        }

        var existing = bindings.findByAgentId(agentId);
        bindings.deleteAll(existing);
        bindings.flush();

        for (var req : valid) {
            if (req.catalogItemId() == null) continue;
            String defaults = req.parameterDefaults();
            if (defaults != null && !defaults.isBlank()) {
                try {
                    json.readTree(defaults);
                } catch (Exception e) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "parameterDefaults must be valid JSON: " + e.getMessage());
                }
            }
            bindings.save(new AgentWorkflowBinding(
                    UUID.randomUUID(),
                    agentId,
                    req.catalogItemId(),
                    blankToNull(req.descriptionOverride()),
                    blankToNull(defaults)));
        }
        return list(agentId);
    }

    private void ensureAgentExists(UUID agentId) {
        if (!agents.existsById(agentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found");
        }
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
