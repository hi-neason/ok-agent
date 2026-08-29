package io.okagent.module.agent.domain;

/**
 * How an agent injects a target user's persona into its system prompt at runtime.
 *
 * <ul>
 *   <li>{@link #NONE} &mdash; do not inject any user persona.</li>
 *   <li>{@link #SELF_ONLY} &mdash; inject only the persona this agent itself extracted/stores.</li>
 *   <li>{@link #GLOBAL} &mdash; inject the merged persona across all agents for this user.</li>
 * </ul>
 */
public enum PersonaInjectionMode {
    NONE,
    SELF_ONLY,
    GLOBAL
}
