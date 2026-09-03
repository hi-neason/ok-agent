package io.okagent.module.workflow.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AgentWorkflowBindingRequest(
        @NotNull UUID catalogItemId,
        @Size(max = 4000) String descriptionOverride,
        @Size(max = 20000) String parameterDefaults) {}
