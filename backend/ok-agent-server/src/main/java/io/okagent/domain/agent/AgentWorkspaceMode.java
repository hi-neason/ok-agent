package io.okagent.domain.agent;

/** Defines the filesystem isolation strategy used by a HarnessAgent. */
public enum AgentWorkspaceMode {
    DISABLED,
    LOCAL_ROOTED,
    DOCKER_SANDBOX
}
