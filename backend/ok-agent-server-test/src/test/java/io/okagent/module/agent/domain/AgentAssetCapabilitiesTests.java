package io.okagent.module.agent.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentAssetCapabilitiesTests {

    @Test
    void initializesCapabilitiesWithSafeDefaults() {
        var agent = new AgentAsset(UUID.randomUUID(), "support", "Support", "", "GENERAL");

        assertThat(agent.getMcpToolFiltersJson()).isEqualTo("{}");
        assertThat(agent.isMemoryEnabled()).isFalse();
        assertThat(agent.getMemoryFlushMode()).isEqualTo(AgentMemoryFlushMode.THROTTLED);
        assertThat(agent.getWorkspaceMode()).isEqualTo(AgentWorkspaceMode.DISABLED);
        assertThat(agent.isShellEnabled()).isFalse();
    }

    @Test
    void updatesMemoryAndWorkspacePolicyAsOneCapabilitySet() {
        var agent = new AgentAsset(UUID.randomUUID(), "support", "Support", "", "GENERAL");

        agent.updateCapabilities(
                "{\"server-id\":[\"clock\"]}",
                true,
                AgentMemoryFlushMode.ALWAYS,
                15,
                45,
                30,
                60,
                AgentWorkspaceMode.DOCKER_SANDBOX,
                "AGENT",
                true,
                false,
                "ubuntu:24.04",
                1024,
                2);

        assertThat(agent.isMemoryEnabled()).isTrue();
        assertThat(agent.getMemoryFlushMode()).isEqualTo(AgentMemoryFlushMode.ALWAYS);
        assertThat(agent.getWorkspaceMode()).isEqualTo(AgentWorkspaceMode.DOCKER_SANDBOX);
        assertThat(agent.getDockerImage()).isEqualTo("ubuntu:24.04");
        assertThat(agent.getSandboxMemoryMb()).isEqualTo(1024);
    }
}
