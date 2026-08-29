package io.okagent.module.agent.application;

/**
 * A single field-level issue produced while validating an agent configuration.
 *
 * <p>Errors block saving; warnings are advisory. The {@code tab} groups the issue with the matching
 * configuration tab in the UI so error counts can be shown per tab and the user can jump to the field.
 */
public record AgentConfigValidationIssue(
        /** Configuration field the issue relates to, e.g. {@code modelAssetId} or {@code dockerImage}. */
        String field,
        /** Stable machine-readable code, e.g. {@code MODEL_NOT_FOUND}, used for i18n lookups. */
        String code,
        /** Human-readable English description; used directly or as an i18n fallback. */
        String message,
        /** Configuration tab that owns the field: core, skills, mcp, memory, workspace, runtime. */
        String tab) {}
