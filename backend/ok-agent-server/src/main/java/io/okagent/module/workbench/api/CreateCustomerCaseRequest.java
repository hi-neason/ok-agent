package io.okagent.module.workbench.api;

import io.okagent.module.workbench.domain.CustomerCaseType;
import jakarta.validation.constraints.NotNull;

public record CreateCustomerCaseRequest(@NotNull CustomerCaseType type) {}
