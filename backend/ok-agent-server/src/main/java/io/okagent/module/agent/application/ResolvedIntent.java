package io.okagent.module.agent.application;
/** Immutable classification rule included in a release snapshot. */
public record ResolvedIntent(String intentKey, String name, String description) {}
