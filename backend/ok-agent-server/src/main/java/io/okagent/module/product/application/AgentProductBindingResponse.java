package io.okagent.module.product.application;

import io.okagent.module.product.domain.AgentProductBinding;
import io.okagent.module.product.domain.ProductBindingScope;
import io.okagent.module.product.domain.ProductCapability;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AgentProductBindingResponse(
        UUID id,
        UUID agentId,
        ProductBindingScope scope,
        String scopeValue,
        Set<ProductCapability> capabilities,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    /** Convenience factory used by management responses; the service builds these directly. */
    public static AgentProductBindingResponse from(AgentProductBinding binding, Set<ProductCapability> capabilities) {
        return new AgentProductBindingResponse(
                binding.getId(),
                binding.getAgentId(),
                binding.getScope(),
                binding.getScopeValue(),
                capabilities,
                binding.isEnabled(),
                binding.getCreatedAt(),
                binding.getUpdatedAt());
    }
}
