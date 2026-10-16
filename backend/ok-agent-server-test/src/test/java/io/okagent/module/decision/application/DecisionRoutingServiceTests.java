package io.okagent.module.decision.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.okagent.module.agent.application.ResolvedAgentConfig;
import io.okagent.module.agent.application.ResolvedDecisionModelAsset;
import io.okagent.module.agent.application.ResolvedIntent;
import io.okagent.module.agent.application.ResolvedSubagent;
import io.okagent.module.model.application.ApiKeyCipher;
import io.okagent.module.model.domain.ModelAsset;
import io.okagent.module.model.infrastructure.persistence.ModelAssetRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DecisionRoutingServiceTests {
    private final ModelAssetRepository models = mock(ModelAssetRepository.class);
    private final ApiKeyCipher cipher = mock(ApiKeyCipher.class);
    private final DecisionModelClient client = mock(DecisionModelClient.class);
    private final ResolvedAgentConfig config = mock(ResolvedAgentConfig.class);
    private final ModelAsset liveAsset = mock(ModelAsset.class);
    private final UUID assetId = UUID.randomUUID();
    private final DecisionRoutingService service = new DecisionRoutingServiceImpl(models, cipher, List.of(client));

    @Test
    void usesFrozenConfigurationAndReadsOnlyLiveCredential() {
        configureModel();
        when(config.getModelTimeoutSeconds()).thenReturn(90);
        when(client.choose(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new DecisionModelClient.Decision("sales", 0.91));

        assertThat(service.choose(config, "Help me buy")).isEqualTo(new DecisionModelClient.Decision("sales", 0.91));

        verify(client).supports("frozen-provider");
        verify(client)
                .choose(
                        "https://frozen.example.invalid",
                        "frozen-model",
                        "decrypted-test-credential",
                        "Help me buy",
                        List.of(new DecisionModelClient.Candidate("sales", "Sales", "Purchasing support")),
                        Duration.ofSeconds(30));
        verify(models).findById(assetId);
        verify(liveAsset).getApiKeyCiphertext();
        verifyNoMoreInteractions(liveAsset, models);
    }

    @Test
    void preservesShorterConfiguredTimeoutAndExplicitNoMatch() {
        configureModel();
        when(config.getModelTimeoutSeconds()).thenReturn(7);
        when(client.choose(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new DecisionModelClient.Decision(null, 0.2));

        assertThat(service.choose(config, "Unrelated question")).isEqualTo(new DecisionModelClient.Decision(null, 0.2));

        verify(client)
                .choose(
                        anyString(),
                        anyString(),
                        anyString(),
                        eq("Unrelated question"),
                        any(),
                        eq(Duration.ofSeconds(7)));
    }

    @Test
    void rejectsUnknownCandidateReturnedByProvider() {
        configureModel();
        when(client.choose(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new DecisionModelClient.Decision("unknown", null));

        assertThatThrownBy(() -> service.choose(config, "Help"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Decision provider returned an unknown candidate");
    }

    @Test
    void rejectsMissingFrozenModelBeforeReadingCredentials() {
        assertThatThrownBy(() -> service.choose(config, "Help"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Decision routing requires a frozen model");
        verifyNoInteractions(models, cipher, client);
    }

    @Test
    void rejectsUnsupportedProviderBeforeReadingCredentials() {
        when(config.getResolvedDecisionModelAsset())
                .thenReturn(new ResolvedDecisionModelAsset(assetId, "unsupported", "model", "https://example.invalid"));

        assertThatThrownBy(() -> service.choose(config, "Help"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsupported decision model provider");
        verifyNoInteractions(models, cipher);
    }

    @Test
    void rejectsMissingCredentialReferenceBeforeCallingProvider() {
        configureModel();
        when(models.findById(assetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.choose(config, "Help"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Decision model credential reference is unavailable");
        verify(client).supports("frozen-provider");
        verifyNoMoreInteractions(client);
        verifyNoInteractions(cipher);
    }

    @Test
    void returnsNoMatchForEmptyCandidatesWithoutDecryptingOrCallingProvider() {
        configureModel();
        when(config.getResolvedIntents()).thenReturn(List.of());

        assertThat(service.choose(config, "Help")).isEqualTo(new DecisionModelClient.Decision(null, null));

        verify(client).supports("frozen-provider");
        verifyNoMoreInteractions(client);
        verifyNoInteractions(cipher, liveAsset);
    }

    @Test
    void rejectsMissingProviderDecision() {
        configureModel();

        assertThatThrownBy(() -> service.choose(config, "Help"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Decision provider returned no decision");
    }

    @Test
    void validatesOptionalConfidenceAtDecisionBoundary() {
        assertThat(new DecisionModelClient.Decision("sales", null).confidence()).isNull();
        assertThat(new DecisionModelClient.Decision("sales", 0.0).confidence()).isZero();
        assertThat(new DecisionModelClient.Decision("sales", 1.0).confidence()).isEqualTo(1.0);
        for (double invalid :
                new double[] {-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThatThrownBy(() -> new DecisionModelClient.Decision("sales", invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Decision confidence must be between zero and one");
        }
    }

    @Test
    void sendsOnlyBoundIntentFromLargeGlobalIntentTree() {
        configureModel();
        var intents = new java.util.ArrayList<ResolvedIntent>();
        intents.add(new ResolvedIntent("sales", "Sales", "Purchasing support"));
        for (int i = 0; i < 254; i++) {
            intents.add(new ResolvedIntent("unrelated-" + i, "Unrelated", "Other agent intent"));
        }
        when(config.getResolvedIntents()).thenReturn(intents);
        when(client.choose(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new DecisionModelClient.Decision("sales", 0.9));

        assertThat(service.choose(config, "Help").key()).isEqualTo("sales");

        verify(client)
                .choose(
                        anyString(),
                        anyString(),
                        anyString(),
                        eq("Help"),
                        eq(List.of(new DecisionModelClient.Candidate("sales", "Sales", "Purchasing support"))),
                        any());
    }

    @Test
    void returnsNoMatchWhenNoSubagentBindsAnIntent() {
        configureModel();
        when(config.getSubagents()).thenReturn(List.of());

        assertThat(service.choose(config, "Help")).isEqualTo(new DecisionModelClient.Decision(null, null));

        verify(client).supports("frozen-provider");
        verifyNoMoreInteractions(client);
        verifyNoInteractions(cipher, liveAsset);
    }

    @Test
    void rejectsUnboundGlobalIntentReturnedByProvider() {
        configureModel();
        when(config.getResolvedIntents())
                .thenReturn(List.of(
                        new ResolvedIntent("sales", "Sales", "Purchasing support"),
                        new ResolvedIntent("unrelated", "Unrelated", "Other agent intent")));
        when(client.choose(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new DecisionModelClient.Decision("unrelated", 0.99));

        assertThatThrownBy(() -> service.choose(config, "Help"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Decision provider returned an unknown candidate");
    }

    private void configureModel() {
        when(config.getSubagents())
                .thenReturn(List.of(new ResolvedSubagent(mock(ResolvedAgentConfig.class), List.of("sales"))));
        when(config.getResolvedDecisionModelAsset())
                .thenReturn(new ResolvedDecisionModelAsset(
                        assetId, "frozen-provider", "frozen-model", "https://frozen.example.invalid"));
        when(client.supports("frozen-provider")).thenReturn(true);
        when(models.findById(assetId)).thenReturn(Optional.of(liveAsset));
        when(liveAsset.getApiKeyCiphertext()).thenReturn("encrypted-test-credential");
        when(cipher.decrypt("encrypted-test-credential")).thenReturn("decrypted-test-credential");
        when(config.getResolvedIntents())
                .thenReturn(List.of(new ResolvedIntent("sales", "Sales", "Purchasing support")));
        when(config.getModelTimeoutSeconds()).thenReturn(10);
    }
}
