package io.okagent.module.workflow.api;

import io.okagent.module.workflow.application.*;
import io.okagent.module.workflow.application.AgentWorkflowBindingService;
import io.okagent.shared.api.Response;
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
    public Response<List<AgentWorkflowBindingResponse>> list(@PathVariable UUID agentId) {
        return Response.success(service.list(agentId));
    }

    /** Replaces the set of workflows bound to an agent. */
    @PutMapping
    public Response<List<AgentWorkflowBindingResponse>> replace(
            @PathVariable UUID agentId, @RequestBody List<AgentWorkflowBindingRequest> bindings) {
        return Response.success(service.replace(agentId, bindings));
    }
}
