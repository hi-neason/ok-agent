package io.okagent.module.workflow.application;

import java.util.UUID;

/** A workflow available to an agent, with agent-local overrides applied. */
public record BoundWorkflow(
        UUID catalogItemId,
        UUID sourceId,
        String sourceKey,
        String remoteWorkflowId,
        String name,
        String description,
        boolean active,
        String parameterDefaultsJson) {}
