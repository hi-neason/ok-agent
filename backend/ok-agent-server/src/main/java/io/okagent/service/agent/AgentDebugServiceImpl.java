package io.okagent.service.agent;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.HarnessAgent;
import io.okagent.domain.agent.AgentAsset;
import io.okagent.repository.agent.AgentAssetRepository;
import io.okagent.web.agent.AgentChatRequest;
import io.okagent.web.agent.AgentChatResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.Exceptions;

@Service
public class AgentDebugServiceImpl implements AgentDebugService {
    private static final Logger log = LoggerFactory.getLogger(AgentDebugServiceImpl.class);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(120);
    private static final int MAX_SESSIONS = 50;

    private final AgentAssetRepository agents;
    private final HarnessAgentFactory factory;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AgentDebugServiceImpl(AgentAssetRepository agents, HarnessAgentFactory factory) {
        this.agents = agents;
        this.factory = factory;
    }

    @Override
    public AgentChatResponse chat(UUID agentId, AgentChatRequest request) {
        var draft = loadDraft(agentId);
        if (draft.getModelAssetId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Select a model in the agent configuration before starting a debug chat");
        }

        var sessionId = resolveSessionId(request.sessionId());
        var session = sessions.compute(sessionId, (key, existing) -> resolveSession(existing, draft));
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Debug session not found or invalid");
        }

        try {
            var ctx = RuntimeContext.builder()
                    .userId("debug")
                    .sessionId(sessionId)
                    .build();
            var reply = session.agent.call(request.message(), ctx).block(CALL_TIMEOUT);
            if (reply == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The agent returned no response");
            }
            return new AgentChatResponse(sessionId, reply.getTextContent());
        } catch (Exception e) {
            throw toUserFacingError(e);
        }
    }

    @Override
    public void resetSession(String sessionId) {
        var removed = sessions.remove(sessionId);
        if (removed != null) {
            closeQuietly(removed.agent);
        }
    }

    private AgentAsset loadDraft(UUID id) {
        return agents.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
    }

    private String resolveSessionId(String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        return "dbg-" + UUID.randomUUID();
    }

    /** Rebuilds the HarnessAgent when missing, bound to another agent, or when config changed. */
    private Session resolveSession(Session existing, AgentAsset draft) {
        if (existing != null
                && existing.agentId.equals(draft.getId())
                && existing.configChangedAt.equals(draft.getUpdatedAt())) {
            return existing;
        }
        if (existing != null) {
            closeQuietly(existing.agent);
        }
        evictIfFull();
        return new Session(draft.getId(), draft.getUpdatedAt(), factory.build(draft));
    }

    private void evictIfFull() {
        if (sessions.size() < MAX_SESSIONS) {
            return;
        }
        sessions.entrySet().stream()
                .min(Map.Entry.comparingByValue((a, b) -> a.lastTouched.compareTo(b.lastTouched)))
                .ifPresent(entry -> {
                    sessions.remove(entry.getKey());
                    closeQuietly(entry.getValue().agent);
                });
    }

    private ResponseStatusException toUserFacingError(Exception e) {
        var unwrapped = Exceptions.unwrap(e);
        log.warn("Debug chat failed: {}", unwrapped.getMessage(), unwrapped);
        var message = unwrapped.getMessage() == null ? "Agent call failed" : unwrapped.getMessage();
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }

    private void closeQuietly(HarnessAgent agent) {
        try {
            agent.close();
        } catch (Exception ignored) {
            // best effort
        }
    }

    private static final class Session {
        private final UUID agentId;
        private final Instant configChangedAt;
        private final HarnessAgent agent;
        private final Instant lastTouched = Instant.now();

        private Session(UUID agentId, Instant configChangedAt, HarnessAgent agent) {
            this.agentId = agentId;
            this.configChangedAt = configChangedAt;
            this.agent = agent;
        }
    }
}
