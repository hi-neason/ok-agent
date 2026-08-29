package io.okagent.module.knowledge.application;

import io.okagent.module.knowledge.application.AgentKnowledgeBindingRequest;
import io.okagent.module.knowledge.application.AgentKnowledgeBindingResponse;
import java.util.List;
import java.util.UUID;

/** Manages which catalog knowledge bases an agent is allowed to search. */
public interface AgentKnowledgeBindingService {

    /** Lists the bindings for an agent with joined knowledge/source display fields. */
    List<AgentKnowledgeBindingResponse> list(UUID agentId);

    /** Replaces the agent's full set of bindings (delete-and-insert within one transaction). */
    List<AgentKnowledgeBindingResponse> replace(UUID agentId, List<AgentKnowledgeBindingRequest> requests);
}
