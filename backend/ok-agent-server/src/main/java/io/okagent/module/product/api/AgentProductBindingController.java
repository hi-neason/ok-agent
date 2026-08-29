package io.okagent.module.product.api;

import io.okagent.module.product.application.*;

import io.okagent.module.product.application.AgentProductBindingService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agents/{agentId}/products")
public class AgentProductBindingController {
    private final AgentProductBindingService service;

    public AgentProductBindingController(AgentProductBindingService service) {
        this.service = service;
    }

    /** Returns an agent's product visibility/capability binding, or null when none exists. */
    @GetMapping
    public AgentProductBindingResponse get(@PathVariable UUID agentId) {
        return service.get(agentId);
    }

    /** Creates or replaces the agent's product binding (scope + capabilities). */
    @PutMapping
    public AgentProductBindingResponse upsert(
            @PathVariable UUID agentId, @Valid @RequestBody AgentProductBindingRequest request) {
        return service.upsert(agentId, request);
    }

    /** Removes the agent's product binding, disabling all product tools. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID agentId) {
        service.delete(agentId);
    }
}
