package io.okagent.module.decision.application;

import io.okagent.module.model.application.ModelConnectionTestResponse;
import io.okagent.module.model.application.ModelConnectionTester;
import io.okagent.module.model.application.OpenAiCompatibleModelConnectionTester;
import io.okagent.module.model.domain.ModelAsset;
import io.okagent.module.model.domain.ModelType;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Dispatches connection probes to the model's actual protocol. */
@Component
@Primary
public class ProviderAwareModelConnectionTester implements ModelConnectionTester {
    private final OpenAiCompatibleModelConnectionTester chat;
    private final List<DecisionModelClient> decisions;

    public ProviderAwareModelConnectionTester(
            OpenAiCompatibleModelConnectionTester chat, List<DecisionModelClient> decisions) {
        this.chat = chat;
        this.decisions = List.copyOf(decisions);
    }

    @Override
    public ModelConnectionTestResponse test(ModelAsset asset, String apiKey) {
        if (asset.getType() != ModelType.DECISION) return chat.test(asset, apiKey);
        var client = decisions.stream()
                .filter(c -> c.supports(asset.getProvider()))
                .findFirst()
                .orElse(null);
        if (client == null) return new ModelConnectionTestResponse(false, 0, "Unsupported decision model provider.");
        try {
            client.choose(
                    asset.getEndpoint(),
                    asset.getModelId(),
                    apiKey,
                    "Connection check",
                    List.of(new DecisionModelClient.Candidate("ok", "OK", "Connection check")),
                    Duration.ofSeconds(30));
            return new ModelConnectionTestResponse(true, 200, "Connection succeeded.");
        } catch (DecisionModelException e) {
            return new ModelConnectionTestResponse(
                    false, e.statusCode(), "Decision model connection failed: " + e.kind());
        } catch (RuntimeException e) {
            return new ModelConnectionTestResponse(false, 0, "Unable to reach the decision model provider.");
        }
    }
}
