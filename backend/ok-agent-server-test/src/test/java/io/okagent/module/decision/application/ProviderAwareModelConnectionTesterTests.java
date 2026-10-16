package io.okagent.module.decision.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.okagent.module.model.application.ModelConnectionTestResponse;
import io.okagent.module.model.application.OpenAiCompatibleModelConnectionTester;
import io.okagent.module.model.domain.ModelAsset;
import io.okagent.module.model.domain.ModelType;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProviderAwareModelConnectionTesterTests {
    private final OpenAiCompatibleModelConnectionTester chat = mock(OpenAiCompatibleModelConnectionTester.class);
    private final DecisionModelClient decision = mock(DecisionModelClient.class);
    private final ProviderAwareModelConnectionTester tester =
            new ProviderAwareModelConnectionTester(chat, List.of(decision));

    @Test
    void probesDecisionModelWithItsSdkInsteadOfChatProtocol() {
        var asset = asset(ModelType.DECISION);
        when(decision.supports("jev")).thenReturn(true);
        when(decision.choose(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new DecisionModelClient.Decision("ok", 0.99));

        assertThat(tester.test(asset, "test-api-key"))
                .isEqualTo(new ModelConnectionTestResponse(true, 200, "Connection succeeded."));
        verify(decision)
                .choose(
                        "https://example.invalid/v1",
                        "test-model",
                        "test-api-key",
                        "Connection check",
                        List.of(new DecisionModelClient.Candidate("ok", "OK", "Connection check")),
                        Duration.ofSeconds(30));
        verifyNoInteractions(chat);
    }

    @Test
    void delegatesNonDecisionModelToChatTester() {
        var asset = asset(ModelType.LLM);
        var expected = new ModelConnectionTestResponse(true, 200, "Chat connection succeeded.");
        when(chat.test(asset, "test-api-key")).thenReturn(expected);

        assertThat(tester.test(asset, "test-api-key")).isEqualTo(expected);
        verify(chat).test(asset, "test-api-key");
        verifyNoInteractions(decision);
    }

    @Test
    void rejectsUnsupportedDecisionProviderWithoutSendingRequest() {
        var result = tester.test(asset(ModelType.DECISION), "test-api-key");

        assertThat(result).isEqualTo(new ModelConnectionTestResponse(false, 0, "Unsupported decision model provider."));
        verify(decision).supports("jev");
        verifyNoMoreInteractions(decision);
        verifyNoInteractions(chat);
    }

    @Test
    void returnsSanitizedFailureWithProviderStatus() {
        when(decision.supports("jev")).thenReturn(true);
        when(decision.choose(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new DecisionModelException(DecisionModelException.Kind.AUTHENTICATION, 401));

        assertThat(tester.test(asset(ModelType.DECISION), "test-api-key"))
                .isEqualTo(new ModelConnectionTestResponse(
                        false, 401, "Decision model connection failed: AUTHENTICATION"));
        verifyNoInteractions(chat);
    }

    private ModelAsset asset(ModelType type) {
        return new ModelAsset(
                UUID.randomUUID(),
                "Test model",
                type,
                "jev",
                "test-model",
                "https://example.invalid/v1",
                "encrypted-test-reference",
                true);
    }
}
