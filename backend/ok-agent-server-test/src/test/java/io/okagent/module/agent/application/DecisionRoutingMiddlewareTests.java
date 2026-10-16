package io.okagent.module.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.AgentInput;
import io.okagent.module.decision.application.DecisionModelClient;
import io.okagent.module.decision.application.DecisionRoutingService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class DecisionRoutingMiddlewareTests {
    private final DecisionRoutingService decisions = mock(DecisionRoutingService.class);
    private final ResolvedAgentConfig config = mock(ResolvedAgentConfig.class);
    private final Msg user =
            Msg.builder().role(MsgRole.USER).textContent("Need help").build();
    private final AgentInput input = new AgentInput(List.of(user));

    private DecisionRoutingMiddleware middleware() {
        var child = mock(ResolvedAgentConfig.class);
        when(child.getAgentKey()).thenReturn("support");
        when(config.getDecisionModelAssetId()).thenReturn(UUID.randomUUID());
        when(config.getResolvedIntents()).thenReturn(List.of(new ResolvedIntent("help", "Help", "Support")));
        when(config.getSubagents()).thenReturn(List.of(new ResolvedSubagent(child, List.of("help"))));
        return new DecisionRoutingMiddleware(config, decisions);
    }

    @Test
    void imLikeTurnRunsDecisionOffCallerThreadAndPreservesOriginalMessages() {
        var middleware = middleware();
        AtomicReference<String> thread = new AtomicReference<>();
        when(decisions.choose(config, "Need help")).thenAnswer(invocation -> {
            thread.set(Thread.currentThread().getName());
            return new DecisionModelClient.Decision("help", 0.9);
        });
        AtomicReference<AgentInput> routed = new AtomicReference<>();
        middleware
                .onAgent(null, RuntimeContext.builder().build(), input, value -> {
                    routed.set(value);
                    return Flux.empty();
                })
                .blockLast();
        assertThat(thread.get()).contains("boundedElastic");
        assertThat(routed.get().msgs()).hasSize(2);
        assertThat(routed.get().msgs().get(0).getRole()).isEqualTo(MsgRole.USER);
        assertThat(routed.get().msgs().get(0).getTextContent()).contains("support", "agent_spawn");
        assertThat(routed.get().msgs().get(1)).isSameAs(user);
        verify(decisions).choose(config, "Need help");
    }

    @Test
    void realHarnessLifecycleAcceptsRoutingMessageAndReachesModel() {
        var middleware = middleware();
        when(decisions.choose(config, "Need help")).thenReturn(new DecisionModelClient.Decision("help", 0.9));
        var model = mock(io.agentscope.core.model.Model.class);
        when(model.getModelName()).thenReturn("mock-model");
        AtomicReference<List<Msg>> received = new AtomicReference<>();
        when(model.stream(anyList(), anyList(), any())).thenAnswer(invocation -> {
            received.set(List.copyOf(invocation.getArgument(0)));
            return Flux.just(io.agentscope.core.model.ChatResponse.builder()
                    .content(List.of(io.agentscope.core.message.TextBlock.builder()
                            .text("Reply")
                            .build()))
                    .build());
        });
        var harness = io.agentscope.harness.agent.HarnessAgent.builder()
                .name("router")
                .sysPrompt("Test router")
                .model(model)
                .middleware(middleware)
                .disableCompaction()
                .disableToolResultEviction()
                .disableSubagents()
                .disableFilesystemTools()
                .disableShellTool()
                .disableMemoryTools()
                .disableMemoryHooks()
                .disableSessionPersistence()
                .disableWorkspaceContext()
                .disableAtPathExpansion()
                .disableDefaultWorkspaceSkills()
                .disableDynamicSkills()
                .disableToolsConfig()
                .build();
        var events = harness.streamEvents(
                        List.of(user),
                        RuntimeContext.builder().sessionId("routing-test").build())
                .collectList()
                .block(java.time.Duration.ofSeconds(10));
        assertThat(events).anyMatch(event -> event instanceof io.agentscope.core.event.AgentResultEvent);
        assertThat(received.get())
                .anyMatch(message -> message.getRole() == MsgRole.USER
                        && message.getTextContent().contains("agent_spawn"));
        assertThat(received.get()).contains(user);
        verify(decisions).choose(config, "Need help");
        when(decisions.choose(config, "Different question")).thenReturn(new DecisionModelClient.Decision(null, null));
        var nextUser = Msg.builder()
                .role(MsgRole.USER)
                .textContent("Different question")
                .build();
        var second = harness.streamEvents(
                        List.of(nextUser),
                        RuntimeContext.builder().sessionId("routing-test").build())
                .collectList()
                .block(java.time.Duration.ofSeconds(10));
        assertThat(second).anyMatch(event -> event instanceof io.agentscope.core.event.AgentResultEvent);
        assertThat(received.get()).contains(nextUser);
        verify(decisions).choose(config, "Different question");
        harness.streamEvents(
                        List.of(),
                        RuntimeContext.builder().sessionId("routing-test").build())
                .collectList()
                .block(java.time.Duration.ofSeconds(10));
        verifyNoMoreInteractions(decisions);
    }

    @Test
    void preclassifiedWebTurnDoesNotRepeatDecision() {
        var middleware = middleware();
        var context = RuntimeContext.builder()
                .put(DecisionRoutingMiddleware.CTX_ALREADY_CLASSIFIED, true)
                .build();
        middleware
                .onAgent(null, context, input, value -> {
                    assertThat(value).isSameAs(input);
                    return Flux.empty();
                })
                .blockLast();
        verifyNoInteractions(decisions);
    }

    @Test
    void noMatchAndLowConfidencePassOriginalInput() {
        var middleware = middleware();
        for (var decision :
                List.of(new DecisionModelClient.Decision(null, null), new DecisionModelClient.Decision("help", 0.59))) {
            when(decisions.choose(config, "Need help")).thenReturn(decision);
            middleware
                    .onAgent(null, RuntimeContext.builder().build(), input, value -> {
                        assertThat(value).isSameAs(input);
                        return Flux.empty();
                    })
                    .blockLast();
        }
    }

    @Test
    void failurePropagatesWithoutCallingAgent() {
        var middleware = middleware();
        when(decisions.choose(config, "Need help")).thenThrow(new IllegalStateException("Decision failed"));
        assertThatThrownBy(() -> middleware
                        .onAgent(null, RuntimeContext.builder().build(), input, value -> {
                            throw new AssertionError("must not call next");
                        })
                        .blockLast())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Decision failed");
    }

    @Test
    void leafAndUnconfiguredAgentDoNotRegisterRouting() {
        middleware();
        assertThat(DecisionRoutingMiddleware.enabled(config, true)).isFalse();
        assertThat(DecisionRoutingMiddleware.enabled(config, false)).isTrue();
        when(config.getDecisionModelAssetId()).thenReturn(null);
        assertThat(DecisionRoutingMiddleware.enabled(config, false)).isFalse();
        new DecisionRoutingMiddleware(config, decisions)
                .onAgent(null, RuntimeContext.builder().build(), input, value -> Flux.empty())
                .blockLast();
        verifyNoInteractions(decisions);
    }
}
