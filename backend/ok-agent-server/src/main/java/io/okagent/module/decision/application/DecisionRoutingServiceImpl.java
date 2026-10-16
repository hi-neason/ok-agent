package io.okagent.module.decision.application;

import io.okagent.module.agent.application.ResolvedAgentConfig;
import io.okagent.module.model.application.ApiKeyCipher;
import io.okagent.module.model.infrastructure.persistence.ModelAssetRepository;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Uses frozen provider settings and resolves only the managed credential reference live. */
@Service
public class DecisionRoutingServiceImpl implements DecisionRoutingService {
    private final ModelAssetRepository models;
    private final ApiKeyCipher cipher;
    private final List<DecisionModelClient> clients;

    public DecisionRoutingServiceImpl(
            ModelAssetRepository models, ApiKeyCipher cipher, List<DecisionModelClient> clients) {
        this.models = models;
        this.cipher = cipher;
        this.clients = List.copyOf(clients);
    }

    @Override
    public DecisionModelClient.Decision choose(ResolvedAgentConfig config, String query) {
        var model = config.getResolvedDecisionModelAsset();
        if (model == null) throw new IllegalStateException("Decision routing requires a frozen model");
        var client = clients.stream()
                .filter(c -> c.supports(model.provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unsupported decision model provider"));
        var secret = models.findById(model.assetId())
                .orElseThrow(() -> new IllegalStateException("Decision model credential reference is unavailable"));
        var boundIntentKeys = config.getSubagents().stream()
                .flatMap(subagent -> subagent.intentKeys().stream())
                .collect(Collectors.toSet());
        var candidates = config.getResolvedIntents().stream()
                .filter(intent -> boundIntentKeys.contains(intent.intentKey()))
                .map(i -> new DecisionModelClient.Candidate(i.intentKey(), i.name(), i.description()))
                .toList();
        if (candidates.isEmpty()) return new DecisionModelClient.Decision(null, null);
        var selected = client.choose(
                model.endpoint(),
                model.modelId(),
                cipher.decrypt(secret.getApiKeyCiphertext()),
                query,
                candidates,
                Duration.ofSeconds(Math.min(config.getModelTimeoutSeconds(), 30)));
        if (selected == null) throw new IllegalStateException("Decision provider returned no decision");
        if (selected.key() != null && candidates.stream().noneMatch(c -> c.key().equals(selected.key()))) {
            throw new IllegalStateException("Decision provider returned an unknown candidate");
        }
        return selected;
    }
}
