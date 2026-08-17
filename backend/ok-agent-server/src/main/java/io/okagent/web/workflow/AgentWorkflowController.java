package io.okagent.web.workflow;

import io.okagent.service.workflow.AgentWorkflowBindingService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agents/{agentId}/workflows")
public class AgentWorkflowController {
    private final AgentWorkflowBindingService service;

    public AgentWorkflowController(AgentWorkflowBindingService service) {
        this.service = service;
    }

    /** Returns the external workflows bound to an agent. */
    @GetMapping
    public List<AgentWorkflowBindingResponse> list(@PathVariable UUID agentId) {
        return service.list(agentId);
    }

    /** Replaces the set of workflows bound to an agent. */
    @PutMapping
    public List<AgentWorkflowBindingResponse> replace(
            @PathVariable UUID agentId, @RequestBody List<AgentWorkflowBindingRequest> bindings) {
        return service.replace(agentId, bindings);
    }
}
