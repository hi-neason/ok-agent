package io.okagent.module.agent.application;

import java.util.List;

/**
 * Structured result of validating an agent configuration.
 *
 * <p>Returned by {@code POST /api/v1/agents/{id}/configuration/validate}. The endpoint never fails for
 * an invalid payload; instead it reports {@code valid=false} together with field-level {@code errors}
 * and advisory {@code warnings}, plus per-area {@code checks} and the elapsed {@code durationMs}.
 */
public record AgentConfigValidationResponse(
        /** True when there are no blocking errors. */
        boolean valid,
        /** Blocking issues that must be resolved before saving. */
        List<AgentConfigValidationIssue> errors,
        /** Advisory issues that do not block saving. */
        List<AgentConfigValidationIssue> warnings,
        /** Per-area evaluation results. */
        List<AgentConfigValidationCheck> checks,
        /** Time spent validating, in milliseconds. */
        long durationMs) {}
