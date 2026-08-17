package io.okagent.web.workflow;

import java.util.UUID;

public record AgentWorkflowBindingRequest(
        UUID catalogItemId,
        String descriptionOverride,
        String parameterDefaults) {}
