package io.okagent.service.workflow;

import io.okagent.web.workflow.AgentWorkflowBindingRequest;
import io.okagent.web.workflow.AgentWorkflowBindingResponse;
import java.util.List;
import java.util.UUID;

/** Manages which catalog workflows an agent is allowed to run. */
public interface AgentWorkflowBindingService {

    /** Lists the bindings for an agent with joined workflow/source display fields. */
    List<AgentWorkflowBindingResponse> list(UUID agentId);

    /** Replaces the agent's full set of bindings (delete-and-insert within one transaction). */
    List<AgentWorkflowBindingResponse> replace(UUID agentId, List<AgentWorkflowBindingRequest> requests);
}
