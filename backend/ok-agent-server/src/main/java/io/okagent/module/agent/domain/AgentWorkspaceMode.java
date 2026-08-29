package io.okagent.module.agent.domain;

/** Defines the filesystem isolation strategy used by a HarnessAgent. */
public enum AgentWorkspaceMode {
    DISABLED,
    LOCAL_ROOTED,
    DOCKER_SANDBOX
}
