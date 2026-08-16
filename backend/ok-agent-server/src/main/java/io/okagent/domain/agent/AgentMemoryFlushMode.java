package io.okagent.domain.agent;

/** Defines when HarnessAgent flushes conversational memory to its workspace. */
public enum AgentMemoryFlushMode {
    ALWAYS,
    THROTTLED,
    NEVER
}
