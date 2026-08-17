package io.okagent.service.knowledge;

import io.okagent.domain.knowledge.AgentKnowledgeBinding;
import io.okagent.domain.knowledge.KnowledgeCatalogItem;
import io.okagent.domain.knowledge.KnowledgeSource;
import io.okagent.repository.knowledge.AgentKnowledgeBindingRepository;
import io.okagent.repository.knowledge.KnowledgeCatalogItemRepository;
import io.okagent.repository.knowledge.KnowledgeSourceRepository;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runtime-facing catalog used by the agent knowledge tools. Resolves an agent's bindings, authorizes
 * retrieval against that agent, and delegates retrieval to the matching {@link KnowledgeProvider}.
 * Retrieval is read-only and high-frequency, so no audit rows are written.
 */
@Service
public class KnowledgeRuntimeCatalog {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeRuntimeCatalog.class);

    private final AgentKnowledgeBindingRepository bindings;
    private final KnowledgeCatalogItemRepository items;
    private final KnowledgeSourceRepository sources;
    private final KnowledgeSourceServiceImpl sourceService;
    private final List<KnowledgeProvider> providers;

    public KnowledgeRuntimeCatalog(
            AgentKnowledgeBindingRepository bindings,
            KnowledgeCatalogItemRepository items,
            KnowledgeSourceRepository sources,
            KnowledgeSourceServiceImpl sourceService,
            List<KnowledgeProvider> providers) {
        this.bindings = bindings;
        this.items = items;
        this.sources = sources;
        this.sourceService = sourceService;
        this.providers = providers;
    }

    /** Returns the enabled knowledge bases bound to the given agent, with overrides applied. */
    public List<BoundKnowledge> listForAgent(UUID agentId) {
        var byItemId = new HashMap<UUID, KnowledgeCatalogItem>();
        for (var item : items.findAllByIdIn(bindingItemIds(agentId))) {
            byItemId.put(item.getId(), item);
        }
        var sourceKeys = sourceKeys(byItemId.values());
        List<BoundKnowledge> result = new ArrayList<>();
        for (var binding : bindings.findByAgentId(agentId)) {
            if (!binding.isEnabled()) continue;
            var item = byItemId.get(binding.getCatalogItemId());
            if (item == null || !item.isActive()) continue;
            String description = binding.getDescriptionOverride();
            if (description == null || description.isBlank()) {
                description = item.getDescription().isBlank() ? item.getRemoteDescription() : item.getDescription();
            }
            result.add(new BoundKnowledge(
                    item.getId(),
                    item.getSourceId(),
                    sourceKeys.getOrDefault(item.getSourceId(), ""),
                    item.getRemoteKnowledgeId(),
                    item.getName(),
                    description == null ? "" : description,
                    item.isActive(),
                    binding.getTopK(),
                    binding.getScoreThreshold()));
        }
        return result;
    }

    /**
     * Authorizes and retrieves chunks from a bound knowledge base.
     *
     * @param topK optional per-call override (null falls back to the binding's value, then provider default)
     */
    public List<RetrievedChunk> retrieve(
            UUID agentId,
            UUID catalogItemId,
            String query,
            Integer topK,
            Double scoreThreshold,
            String userId,
            String sessionId) {
        var item = requireBoundItem(agentId, catalogItemId);
        var source = sources.findById(item.getSourceId())
                .orElseThrow(() -> new IllegalStateException("Knowledge source not found: " + item.getSourceId()));
        if (!source.isEnabled()) {
            throw new IllegalStateException("Knowledge source '" + source.getName() + "' is disabled");
        }
        var binding = bindings.findByAgentId(agentId).stream()
                .filter(b -> b.getCatalogItemId().equals(catalogItemId))
                .findFirst()
                .orElse(null);

        Integer effectiveTopK = topK != null ? topK : (binding == null ? null : binding.getTopK());
        Double effectiveThreshold = scoreThreshold != null
                ? scoreThreshold
                : (binding == null ? null : binding.getScoreThreshold());

        var provider = providerFor(source);
        KnowledgeSourceConfig config = sourceService.toConfig(source);
        return provider.retrieve(
                config, item.getRemoteKnowledgeId(), query, effectiveTopK, effectiveThreshold, userId);
    }

    private KnowledgeCatalogItem requireBoundItem(UUID agentId, UUID catalogItemId) {
        boolean bound = bindings.findByAgentId(agentId).stream()
                .anyMatch(b -> b.isEnabled() && b.getCatalogItemId().equals(catalogItemId));
        if (!bound) {
            throw new SecurityException("Knowledge base is not bound to this agent: " + catalogItemId);
        }
        return items.findById(catalogItemId)
                .orElseThrow(() -> new IllegalStateException("Knowledge catalog item not found: " + catalogItemId));
    }

    private List<UUID> bindingItemIds(UUID agentId) {
        return bindings.findByAgentId(agentId).stream()
                .filter(AgentKnowledgeBinding::isEnabled)
                .map(AgentKnowledgeBinding::getCatalogItemId)
                .toList();
    }

    private Map<UUID, String> sourceKeys(Collection<KnowledgeCatalogItem> items) {
        var ids = items.stream().map(KnowledgeCatalogItem::getSourceId).distinct().toList();
        Map<UUID, String> keys = new HashMap<>();
        for (var source : sources.findAllById(ids)) {
            keys.put(source.getId(), source.getSourceKey());
        }
        return keys;
    }

    private KnowledgeProvider providerFor(KnowledgeSource source) {
        return providers.stream()
                .filter(p -> p.type().equalsIgnoreCase(source.getSourceType().name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No knowledge provider for type " + source.getSourceType()));
    }
}
