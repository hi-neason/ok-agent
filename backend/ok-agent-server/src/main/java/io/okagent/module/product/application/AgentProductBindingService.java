package io.okagent.module.product.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.module.product.domain.AgentProductBinding;
import io.okagent.module.product.domain.ProductBindingScope;
import io.okagent.module.product.domain.ProductCapability;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.product.infrastructure.persistence.AgentProductBindingRepository;
import io.okagent.module.product.application.AgentProductBindingRequest;
import io.okagent.module.product.application.AgentProductBindingResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentProductBindingService {
    private final AgentProductBindingRepository bindings;
    private final AgentAssetRepository agents;
    private final ObjectMapper json = new ObjectMapper();

    public AgentProductBindingService(AgentProductBindingRepository bindings, AgentAssetRepository agents) {
        this.bindings = bindings;
        this.agents = agents;
    }

    @Transactional(readOnly = true)
    public AgentProductBindingResponse get(UUID agentId) {
        ensureAgentExists(agentId);
        return bindings.findByAgentId(agentId).map(this::toResponse).orElse(null);
    }

    @Transactional
    public AgentProductBindingResponse upsert(UUID agentId, AgentProductBindingRequest request) {
        ensureAgentExists(agentId);
        ProductBindingScope scope = request.scope() == null ? ProductBindingScope.ALL : request.scope();
        validate(scope, request.scopeValue(), request.capabilities());

        AgentProductBinding binding = bindings.findByAgentId(agentId).orElse(null);
        String capabilitiesJson = writeCapabilities(request.capabilities());
        if (binding == null) {
            binding = new AgentProductBinding(
                    UUID.randomUUID(), agentId, scope, request.scopeValue(), capabilitiesJson);
        } else {
            binding.apply(scope, request.scopeValue(), capabilitiesJson, request.enabled());
        }
        return toResponse(bindings.save(binding));
    }

    @Transactional
    public void delete(UUID agentId) {
        bindings.deleteByAgentId(agentId);
    }

    private void ensureAgentExists(UUID agentId) {
        if (!agents.existsById(agentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found");
        }
    }

    private void validate(
            ProductBindingScope scope, String scopeValue, List<ProductCapability> capabilities) {
        if ((scope == ProductBindingScope.CATEGORY) && (scopeValue == null || scopeValue.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scopeValue is required for CATEGORY scope");
        }
        if ((scope == ProductBindingScope.TAG || scope == ProductBindingScope.EXPLICIT)
                && (scopeValue == null || scopeValue.isBlank() || !isValidJsonArray(scopeValue))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "scopeValue must be a JSON array for TAG/EXPLICIT scope");
        }
        if (capabilities == null || capabilities.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one capability is required");
        }
        if (capabilities.contains(ProductCapability.RECOMMEND) && !capabilities.contains(ProductCapability.QUERY)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RECOMMEND requires QUERY");
        }
        if (capabilities.contains(ProductCapability.SOLUTION) && !capabilities.contains(ProductCapability.QUERY)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SOLUTION requires QUERY");
        }
    }

    private boolean isValidJsonArray(String value) {
        try {
            List<?> parsed = json.readValue(value, new TypeReference<List<?>>() {});
            return parsed != null;
        } catch (Exception e) {
            return false;
        }
    }

    private String writeCapabilities(List<ProductCapability> capabilities) {
        try {
            return json.writeValueAsString(new HashSet<>(capabilities));
        } catch (Exception e) {
            return "[]";
        }
    }

    private Set<ProductCapability> readCapabilities(String value) {
        if (value == null || value.isBlank()) return Set.of();
        try {
            List<String> names = json.readValue(value, new TypeReference<List<String>>() {});
            Set<ProductCapability> out = new HashSet<>();
            for (String name : names) {
                try {
                    out.add(ProductCapability.valueOf(name));
                } catch (IllegalArgumentException ignored) {
                    // skip unknown capability from newer versions
                }
            }
            return out;
        } catch (Exception e) {
            return Set.of();
        }
    }

    private AgentProductBindingResponse toResponse(AgentProductBinding b) {
        return new AgentProductBindingResponse(
                b.getId(),
                b.getAgentId(),
                b.getScope(),
                b.getScopeValue(),
                readCapabilities(b.getCapabilitiesJson()),
                b.isEnabled(),
                b.getCreatedAt(),
                b.getUpdatedAt());
    }
}
