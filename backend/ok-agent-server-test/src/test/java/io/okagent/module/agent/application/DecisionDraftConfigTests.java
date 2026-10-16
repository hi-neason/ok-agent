package io.okagent.module.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.okagent.module.agent.domain.AgentAsset;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.intent.application.IntentDto;
import io.okagent.module.intent.application.IntentNode;
import io.okagent.module.intent.application.IntentService;
import io.okagent.module.model.domain.ModelAsset;
import io.okagent.module.model.domain.ModelType;
import io.okagent.module.model.infrastructure.persistence.ModelAssetRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DecisionDraftConfigTests {
    @Mock
    ModelAssetRepository models;

    @Mock
    AgentAssetRepository agents;

    @Mock
    IntentService intents;

    @InjectMocks
    HarnessAgentFactory factory;

    @Test
    void debugResolvesCurrentDecisionConfigurationAndIntents() {
        var draft = new AgentAsset(UUID.randomUUID(), "router", "Router", "", "GENERAL");
        UUID id = UUID.randomUUID();
        draft.setDecisionModelAssetId(id);
        var model = new ModelAsset(
                id, "Decision", ModelType.DECISION, "jev", "current-model", "https://example.com", "enc", true);
        when(models.findById(id)).thenReturn(Optional.of(model));
        when(intents.getTree())
                .thenReturn(List.of(new IntentNode(
                        new IntentDto(UUID.randomUUID(), null, "help", "Help", "Support", List.of(), 0, null, null),
                        List.of())));
        var config = factory.draftConfig(draft);
        assertThat(config.getResolvedDecisionModelAsset())
                .isEqualTo(new ResolvedDecisionModelAsset(id, "jev", "current-model", "https://example.com"));
        assertThat(config.getResolvedIntents()).containsExactly(new ResolvedIntent("help", "Help", "Support"));
        model.update("Decision", ModelType.DECISION, "jev", "changed", "https://example.com", null, true);
        assertThat(factory.draftConfig(draft).contentHash()).isNotEqualTo(config.contentHash());
    }

    @Test
    void debugRejectsMissingDecisionModelInsteadOfIgnoringConfiguration() {
        var draft = new AgentAsset(UUID.randomUUID(), "router", "Router", "", "GENERAL");
        draft.setDecisionModelAssetId(UUID.randomUUID());
        assertThatThrownBy(() -> factory.draftConfig(draft)).isInstanceOf(IllegalStateException.class);
    }
}
