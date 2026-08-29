package io.okagent.module.workflow.api;

import jakarta.validation.constraints.Size;

public record WorkflowDescriptionUpdateRequest(@Size(max = 4000) String description) {}
