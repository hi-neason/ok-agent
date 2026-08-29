package io.okagent.module.agent.domain;

/** Defines when HarnessAgent flushes conversational memory to its workspace. */
public enum AgentMemoryFlushMode {
    ALWAYS,
    THROTTLED,
    NEVER
}
