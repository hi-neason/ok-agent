package io.okagent.module.knowledge.application;

import io.okagent.module.knowledge.domain.AgentKnowledgeBinding;
import io.okagent.module.knowledge.domain.KnowledgeCatalogItem;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.knowledge.infrastructure.persistence.AgentKnowledgeBindingRepository;
import io.okagent.module.knowledge.infrastructure.persistence.KnowledgeCatalogItemRepository;
import io.okagent.module.knowledge.infrastructure.persistence.KnowledgeSourceRepository;
import io.okagent.module.knowledge.application.AgentKnowledgeBindingRequest;
import io.okagent.module.knowledge.application.AgentKnowledgeBindingResponse;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentKnowledgeBindingServiceImpl implements AgentKnowledgeBindingService {
    private static final int MIN_TOP_K = 1;
    private static final int MAX_TOP_K = 50;

    private final AgentKnowledgeBindingRepository bindings;
    private final KnowledgeCatalogItemRepository items;
    private final KnowledgeSourceRepository sources;
    private final AgentAssetRepository agents;

    public AgentKnowledgeBindingServiceImpl(
            AgentKnowledgeBindingRepository bindings,
            KnowledgeCatalogItemRepository items,
            KnowledgeSourceRepository sources,
            AgentAssetRepository agents) {
        this.bindings = bindings;
        this.items = items;
        this.sources = sources;
        this.agents = agents;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentKnowledgeBindingResponse> list(UUID agentId) {
        ensureAgentExists(agentId);
        var byId = new HashMap<UUID, KnowledgeCatalogItem>();
        for (var item : items.findAll()) {
            byId.put(item.getId(), item);
        }
        var sourceNames = new HashMap<UUID, String>();
        sources.findAll().forEach(s -> sourceNames.put(s.getId(), s.getName()));
        return bindings.findByAgentId(agentId).stream()
                .sorted(Comparator.comparing(AgentKnowledgeBinding::getCreatedAt))
                .map(b -> {
                    var item = byId.get(b.getCatalogItemId());
                    return new AgentKnowledgeBindingResponse(
                            b.getId(),
                            b.getAgentId(),
                            b.getCatalogItemId(),
                            item == null ? "" : item.getRemoteKnowledgeId(),
                            item == null ? "(missing)" : item.getName(),
                            item == null ? "" : sourceNames.getOrDefault(item.getSourceId(), ""),
                            b.getDescriptionOverride(),
                            b.getTopK(),
                            b.getScoreThreshold(),
                            b.isEnabled(),
                            item == null ? "MISSING" : item.getMetadataStatus().name(),
                            item != null && item.isActive(),
                            b.getUpdatedAt());
                })
                .toList();
    }

    @Override
    @Transactional
    public List<AgentKnowledgeBindingResponse> replace(UUID agentId, List<AgentKnowledgeBindingRequest> requests) {
        ensureAgentExists(agentId);
        var valid = requests == null ? List.<AgentKnowledgeBindingRequest>of() : requests;
        for (var req : valid) {
            if (req.catalogItemId() != null
                    && items.findById(req.catalogItemId()).isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Knowledge catalog item does not exist: " + req.catalogItemId());
            }
            if (req.topK() != null && (req.topK() < MIN_TOP_K || req.topK() > MAX_TOP_K)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "topK must be between " + MIN_TOP_K + " and " + MAX_TOP_K);
            }
            if (req.scoreThreshold() != null && (req.scoreThreshold() < 0 || req.scoreThreshold() > 1)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scoreThreshold must be between 0 and 1");
            }
        }

        var existing = bindings.findByAgentId(agentId);
        bindings.deleteAll(existing);
        bindings.flush();

        for (var req : valid) {
            if (req.catalogItemId() == null) continue;
            bindings.save(new AgentKnowledgeBinding(
                    UUID.randomUUID(),
                    agentId,
                    req.catalogItemId(),
                    blankToNull(req.descriptionOverride()),
                    req.topK(),
                    req.scoreThreshold()));
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
