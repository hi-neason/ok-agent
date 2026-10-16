package io.okagent.module.agentruntime.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.okagent.module.agent.application.*;
import io.okagent.module.decision.application.DecisionModelClient.Decision;
import io.okagent.module.decision.application.DecisionRoutingService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DecisionIntentRoutingTests {
    private final DecisionRoutingService decisions = mock(DecisionRoutingService.class);
    private final HarnessAgentFactory factory = mock(HarnessAgentFactory.class);
    private final ResolvedAgentConfig router = mock(ResolvedAgentConfig.class);
    private final ReleasedAgentChatService service = new ReleasedAgentChatService(
            null, null, factory, null, null, new com.fasterxml.jackson.databind.ObjectMapper(), null, decisions);

    private void prepare() {
        var child = mock(ResolvedAgentConfig.class);
        when(child.getAgentKey()).thenReturn("sales-agent");
        when(router.getSubagents()).thenReturn(List.of(new ResolvedSubagent(child, List.of("sales"))));
        when(router.getResolvedIntents())
                .thenReturn(List.of(new ResolvedIntent("sales", "Sales", "Purchase requests")));
        when(router.getDecisionModelAssetId()).thenReturn(UUID.randomUUID());
    }

    @Test
    void delegatesBoundedChoiceWithoutInventingConfidence() {
        prepare();
        when(decisions.choose(router, "buy")).thenReturn(new Decision("sales", null));
        var result = service.classify("buy", router);
        assertThat(result.targetSubagentKey()).isEqualTo("sales-agent");
        assertThat(result.confidence()).isNull();
        assertThat(result.fallback()).isFalse();
        verifyNoInteractions(factory);
    }

    @Test
    void noMatchAndUnknownChoiceFallBackWithoutLlmRetry() {
        prepare();
        when(decisions.choose(router, "none")).thenReturn(new Decision(null, 0.8));
        when(decisions.choose(router, "unknown")).thenReturn(new Decision("invented", 0.8));
        when(decisions.choose(router, "failure")).thenThrow(new IllegalStateException("unavailable"));
        for (String query : List.of("none", "unknown")) {
            assertThat(service.classify(query, router).fallback()).isTrue();
        }
        assertThatThrownBy(() -> service.classify("failure", router))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("502");
        verifyNoInteractions(factory);
    }

    @Test
    void lowConfidenceDecisionFallsBack() {
        prepare();
        when(decisions.choose(router, "buy")).thenReturn(new Decision("sales", 0.2));
        var result = service.classify("buy", router);
        assertThat(result.fallback()).isTrue();
        assertThat(result.confidence()).isEqualTo(0.2);
        assertThat(result.targetSubagentKey()).isNull();
    }

    @Test
    void legacyConfigurationKeepsLlmClassification() {
        prepare();
        when(router.getDecisionModelAssetId()).thenReturn(null);
        when(factory.classify(eq(router), anyString())).thenReturn("{\"intentKey\":\"sales\",\"confidence\":0.9}");
        var result = service.classify("buy", router);
        assertThat(result.targetSubagentKey()).isEqualTo("sales-agent");
        assertThat(result.confidence()).isEqualTo(0.9);
        verifyNoInteractions(decisions);
    }
}
