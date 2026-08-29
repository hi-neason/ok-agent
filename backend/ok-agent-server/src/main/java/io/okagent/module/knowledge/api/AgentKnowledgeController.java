package io.okagent.module.knowledge.api;

import io.okagent.module.knowledge.application.*;

import io.okagent.module.knowledge.application.AgentKnowledgeBindingService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agents/{agentId}/knowledge")
public class AgentKnowledgeController {
    private final AgentKnowledgeBindingService service;

    public AgentKnowledgeController(AgentKnowledgeBindingService service) {
        this.service = service;
    }

    /** Returns the external knowledge bases bound to an agent. */
    @GetMapping
    public List<AgentKnowledgeBindingResponse> list(@PathVariable UUID agentId) {
        return service.list(agentId);
    }

    /** Replaces the set of knowledge bases bound to an agent. */
    @PutMapping
    public List<AgentKnowledgeBindingResponse> replace(
            @PathVariable UUID agentId, @RequestBody List<AgentKnowledgeBindingRequest> bindings) {
        return service.replace(agentId, bindings);
    }
}
