package io.okagent.module.workflow.application;

import java.util.UUID;

public record AgentWorkflowBindingRequest(UUID catalogItemId, String descriptionOverride, String parameterDefaults) {}
