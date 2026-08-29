package io.okagent.module.agent.application;

/**
 * A named validation check with a pass/fail outcome and an optional human-readable detail.
 *
 * <p>Checks give the UI a structured, machine-readable view of which constraint areas were evaluated,
 * independent of the human-facing error/warning lists.
 */
public record AgentConfigValidationCheck(
        /** Stable check name, e.g. {@code model.resolvable} or {@code mcp.servers.resolved}. */
        String name,
        /** Whether the check passed. */
        boolean passed,
        /** Optional detail describing the outcome. */
        String detail) {}
