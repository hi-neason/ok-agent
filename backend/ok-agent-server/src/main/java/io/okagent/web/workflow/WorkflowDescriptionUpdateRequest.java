package io.okagent.web.workflow;

import jakarta.validation.constraints.Size;

public record WorkflowDescriptionUpdateRequest(@Size(max = 4000) String description) {}
