package io.okagent.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.harness.agent.HarnessAgent;
import io.okagent.domain.agent.AgentAsset;
import io.okagent.domain.agent.AgentPermissionMode;
import io.okagent.repository.agent.AgentAssetRepository;
import io.okagent.web.agent.AgentChatRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
        when(agents.findById(agentId)).thenReturn(Optional.of(asset));
        when(factory.build(asset)).thenReturn(harnessAgent);
        when(harnessAgent.streamEvents(any(String.class), any(RuntimeContext.class)))
                .thenReturn(Flux.just(new AgentResultEvent(new AssistantMessage("Current time returned by tool"))));

        var service = new AgentDebugServiceImpl(agents, factory);
        var response = service.chat(agentId, new AgentChatRequest("Call the current time tool", "debug-session"));

        verify(harnessAgent)
                .setPermissionMode(
                        argThat(context ->
                                "debug".equals(context.getUserId()) && "debug-session".equals(context.getSessionId())),
                        eq(PermissionMode.EXPLORE));
        assertThat(response.reply()).isEqualTo("Current time returned by tool");
    }
}
