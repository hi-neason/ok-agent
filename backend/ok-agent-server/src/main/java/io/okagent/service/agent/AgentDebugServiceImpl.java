package io.okagent.service.agent;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.permission.PermissionMode;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
            // The synchronous debug API has no human-in-the-loop confirmation round trip. The
            // configured policy therefore decides whether tool calls run or stop for approval.
            session.agent.setPermissionMode(
                    ctx, PermissionMode.valueOf(draft.getPermissionMode().name()));

            // Accumulate the final reply from text deltas (the demo's approach). Some models /
            // transports emit the answer only via TextBlockDeltaEvent and leave the final
            // AgentResultEvent text empty, especially across multi-turn tool calls.
            var answer = new StringBuilder();
            var finalMsg = new AtomicReference<Msg>();
            var toolCalled = new AtomicBoolean(false);
            var toolResultSeen = new AtomicBoolean(false);

            session.agent
                    .streamEvents(request.message(), ctx)
                    .doOnNext(event -> {
                        if (event instanceof TextBlockDeltaEvent delta) {
                            answer.append(delta.getDelta());
                        } else if (event instanceof ToolCallStartEvent) {
                            toolCalled.set(true);
                        } else if (event instanceof ToolResultEndEvent) {
                            toolResultSeen.set(true);
                        } else if (event instanceof AgentResultEvent result) {
                            finalMsg.set(result.getResult());
                        }
                    })
                    .blockLast(CALL_TIMEOUT);

            // Prefer the streamed text; fall back to the result message's text content.
            String text = answer.toString();
            if (text.isBlank() && finalMsg.get() != null) {
                text = finalMsg.get().getTextContent();
            }
            if (text != null && !text.isBlank()) {
                return new AgentChatResponse(sessionId, text.trim());
            }

            log.warn(
                    "Debug chat empty; toolCalled={} toolResultSeen={} finalMsg={}",
                    toolCalled.get(),
                    toolResultSeen.get(),
                    finalMsg.get() != null);

            if (toolCalled.get() && !toolResultSeen.get()) {
                return new AgentChatResponse(
                        sessionId,
                        "The model decided to call a tool but the call did not complete. Check that"
                                + " the MCP server is reachable and that the model supports function"
                                + " calling.");
            }
            if (toolResultSeen.get()) {
                return new AgentChatResponse(
                        sessionId,
                        "The tool was called and returned a result, but the model did not produce a"
                                + " final answer. This usually means the selected model does not fully"
                                + " support multi-turn tool calling; try a model with confirmed"
                                + " tool-calling support.");
            }
            return new AgentChatResponse(
                    sessionId,
                    "The model returned an empty response without calling any tool. Try rephrasing"
                            + " your message or select a different model.");
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
