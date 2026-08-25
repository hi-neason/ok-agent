package io.okagent.web.customerwork;

import io.okagent.domain.customerwork.CustomerCaseType;
import jakarta.validation.constraints.NotNull;

public record CreateCustomerCaseRequest(@NotNull CustomerCaseType type) {}
