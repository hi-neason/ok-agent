package io.okagent.module.product.application;

import io.okagent.module.product.domain.ProductBindingScope;
import io.okagent.module.product.domain.ProductCapability;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AgentProductBindingRequest(
        @NotNull ProductBindingScope scope,
        String scopeValue,
        @NotNull List<ProductCapability> capabilities,
        boolean enabled) {}
