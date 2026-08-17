package io.okagent.service.agent;

import io.okagent.web.agent.AgentChatRequest;
import io.okagent.web.agent.AgentChatResponse;
import java.util.UUID;

public interface AgentDebugService {
    /**
     * Sends one user message to a debug session and returns the agent reply. When the request omits a
     * session id, a new session is created (persisted via the shared DialogueService) and its id is
     * returned.
     */
    AgentChatResponse chat(UUID agentId, AgentChatRequest request);

    /** Closes and discards a debug session, freeing the underlying HarnessAgent and all stored data. */
    void resetSession(String sessionId);
}
