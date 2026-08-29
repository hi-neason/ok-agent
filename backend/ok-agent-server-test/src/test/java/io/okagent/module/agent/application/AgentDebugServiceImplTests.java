package io.okagent.module.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.harness.agent.HarnessAgent;
import io.okagent.module.agent.domain.AgentAsset;
import io.okagent.module.agent.domain.AgentPermissionMode;
import io.okagent.module.conversation.domain.DialogueSession;
import io.okagent.infrastructure.store.JdbcAgentStateStore;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.conversation.application.DialogueService;
import io.okagent.module.persona.application.PersonaExtractionService;
import io.okagent.module.agent.application.AgentChatRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

class AgentDebugServiceImplTests {
    @Test
    void shouldBypassInteractivePermissionGateInDebugSession() {
        var agentId = UUID.randomUUID();
        var asset = new AgentAsset(agentId, "time-agent", "Time agent", "", "testing");
        asset.updateConfiguration("", "", UUID.randomUUID(), 0.7, null, null, 2048, "[]", "[]");
        asset.updateRuntimePolicy(12, 90, 45, 1, AgentPermissionMode.EXPLORE, false, true, 16000, true, false);

        var agents = mock(AgentAssetRepository.class);
        var factory = mock(HarnessAgentFactory.class);
        var harnessAgent = mock(HarnessAgent.class);
        var dialogue = mock(DialogueService.class);
        var stateStore = mock(JdbcAgentStateStore.class);
        var personaExtraction = mock(PersonaExtractionService.class);
        when(agents.findById(agentId)).thenReturn(Optional.of(asset));
        var debugConfig = new DraftAgentConfig(asset, List.of());
        when(factory.draftConfig(asset)).thenReturn(debugConfig);
        when(factory.build(debugConfig, "debug")).thenReturn(harnessAgent);
        when(dialogue.nextSeq(any(String.class))).thenReturn(1);
        when(harnessAgent.streamEvents(any(String.class), any(RuntimeContext.class)))
                .thenReturn(Flux.just(new AgentResultEvent(new AssistantMessage("Current time returned by tool"))));

        var service = new AgentDebugServiceImpl(agents, factory, dialogue, stateStore, personaExtraction);
        var response =
                service.chat(agentId, new AgentChatRequest("Call the current time tool", "debug-session", "debug"));

        verify(harnessAgent)
                .setPermissionMode(
                        argThat(context ->
                                "debug".equals(context.getUserId()) && "debug-session".equals(context.getSessionId())),
                        eq(PermissionMode.EXPLORE));
        assertThat(response.reply()).isEqualTo("Current time returned by tool");
    }

    @Test
    void shouldRecordBothTurnsThroughTheSharedDialogueService() {
        var agentId = UUID.randomUUID();
        var asset = new AgentAsset(agentId, "time-agent", "Time agent", "", "testing");
        asset.updateConfiguration("", "", UUID.randomUUID(), 0.7, null, null, 2048, "[]", "[]");
        asset.updateRuntimePolicy(12, 90, 45, 1, AgentPermissionMode.EXPLORE, false, true, 16000, true, false);

        var agents = mock(AgentAssetRepository.class);
        var factory = mock(HarnessAgentFactory.class);
        var harnessAgent = mock(HarnessAgent.class);
        var dialogue = mock(DialogueService.class);
        var personaExtraction = mock(PersonaExtractionService.class);
        when(agents.findById(agentId)).thenReturn(Optional.of(asset));
        var debugConfig = new DraftAgentConfig(asset, List.of());
        when(factory.draftConfig(asset)).thenReturn(debugConfig);
        when(factory.build(debugConfig, "debug")).thenReturn(harnessAgent);
        when(dialogue.sessionExists("dlg-session")).thenReturn(false);
        when(dialogue.nextSeq("dlg-session")).thenReturn(2);
        when(harnessAgent.streamEvents(any(String.class), any(RuntimeContext.class)))
                .thenReturn(Flux.just(new AgentResultEvent(new AssistantMessage("Beijing time is 09:15"))));

        var service = new AgentDebugServiceImpl(
                agents, factory, dialogue, mock(JdbcAgentStateStore.class), personaExtraction);
        service.chat(agentId, new AgentChatRequest("What time is it?", "dlg-session", "debug"));

        // The debug runtime must not own its own history table: it records through the shared
        // DialogueService so the 运行观测 module sees debug and production conversations alike.
        verify(dialogue).ensureSession(eq("dlg-session"), eq(agentId), eq("debug"), eq("What time is it?"));
        verify(dialogue)
                .recordMessage(eq("dlg-session"), eq("user"), eq("What time is it?"), eq(null), eq(null), eq(null));
        verify(dialogue)
                .recordMessage(
                        eq("dlg-session"),
                        eq("assistant"),
                        eq("Beijing time is 09:15"),
                        eq(null),
                        any(Integer.class),
                        any(String.class));
        verify(dialogue).touchSession("dlg-session");
    }

    @Test
    void shouldNotResetAnotherUsersSession() {
        var dialogue = mock(DialogueService.class);
        var stateStore = mock(JdbcAgentStateStore.class);
        var existing = new DialogueSession(
                "private-session", UUID.randomUUID(), "Private", "owner", java.time.Instant.now());
        when(dialogue.findById("private-session")).thenReturn(Optional.of(existing));
        var service = new AgentDebugServiceImpl(
                mock(AgentAssetRepository.class),
                mock(HarnessAgentFactory.class),
                dialogue,
                stateStore,
                mock(PersonaExtractionService.class));

        assertThatThrownBy(() -> service.resetSession("private-session", "attacker"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(dialogue, never()).purge("private-session");
        verifyNoInteractions(stateStore);
    }

    @Test
    void shouldRejectConcurrentCallsForTheSameSession() throws Exception {
        var agentId = UUID.randomUUID();
        var asset = new AgentAsset(agentId, "agent", "Agent", "", "testing");
        asset.updateConfiguration("", "", UUID.randomUUID(), 0.7, null, null, 2048, "[]", "[]");
        var agents = mock(AgentAssetRepository.class);
        var factory = mock(HarnessAgentFactory.class);
        var harnessAgent = mock(HarnessAgent.class);
        var dialogue = mock(DialogueService.class);
        var config = new DraftAgentConfig(asset, List.of());
        when(agents.findById(agentId)).thenReturn(Optional.of(asset));
        when(factory.draftConfig(asset)).thenReturn(config);
        when(factory.build(config, "user")).thenReturn(harnessAgent);
        when(dialogue.nextSeq("session")).thenReturn(1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(harnessAgent.streamEvents(any(String.class), any(RuntimeContext.class)))
                .thenReturn(Flux.create(sink -> {
                    entered.countDown();
                    try {
                        release.await();
                        sink.next(new AgentResultEvent(new AssistantMessage("done")));
                        sink.complete();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        sink.error(exception);
                    }
                }));
        var service = new AgentDebugServiceImpl(
                agents,
                factory,
                dialogue,
                mock(JdbcAgentStateStore.class),
                mock(PersonaExtractionService.class));
        var request = new AgentChatRequest("message", "session", "user");

        var first = CompletableFuture.supplyAsync(() -> service.chat(agentId, request));
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        try {
            assertThatThrownBy(() -> service.chat(agentId, request))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        } finally {
            release.countDown();
        }

        assertThat(first.get(2, TimeUnit.SECONDS).reply()).isEqualTo("done");
    }
}
