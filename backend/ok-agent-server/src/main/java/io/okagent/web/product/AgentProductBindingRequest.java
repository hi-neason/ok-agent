package io.okagent.web.product;

import io.okagent.domain.product.ProductBindingScope;
import io.okagent.domain.product.ProductCapability;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AgentProductBindingRequest(
        @NotNull ProductBindingScope scope,
        String scopeValue,
        @NotNull List<ProductCapability> capabilities,
        boolean enabled) {}
