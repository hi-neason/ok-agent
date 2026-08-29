package io.okagent.module.agent.domain;

/** Permission policy applied to tools invoked by an agent runtime session. */
public enum AgentPermissionMode {
    DEFAULT,
    EXPLORE,
    ACCEPT_EDITS,
    DONT_ASK,
    BYPASS
}
