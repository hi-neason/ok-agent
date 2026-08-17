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
import io.okagent.infrastructure.store.JdbcAgentStateStore;
import io.okagent.infrastructure.store.JdbcTranscriptStore;
import io.okagent.repository.agent.AgentAssetRepository;
import io.okagent.service.dialogue.DialogueService;
import io.okagent.web.agent.AgentChatRequest;
import io.okagent.web.agent.AgentChatResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private static final String DEBUG_USER = "debug";

    private final AgentAssetRepository agents;
    private final HarnessAgentFactory factory;
    private final DialogueService dialogue;
    private final JdbcAgentStateStore stateStore;
    private final JdbcTranscriptStore transcriptStore;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AgentDebugServiceImpl(
            AgentAssetRepository agents,
            HarnessAgentFactory factory,
            DialogueService dialogue,
            JdbcAgentStateStore stateStore,
            JdbcTranscriptStore transcriptStore) {
        this.agents = agents;
        this.factory = factory;
        this.dialogue = dialogue;
        this.stateStore = stateStore;
        this.transcriptStore = transcriptStore;
    }

    @Override
    public AgentChatResponse chat(UUID agentId, AgentChatRequest request) {
        var draft = loadDraft(agentId);
        if (draft.getModelAssetId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Select a model in the agent configuration before starting a debug chat");
        }

        var sessionId = resolveSessionId(request.sessionId());
        var session = sessions.compute(sessionId, (key, existing) -> resolveSession(sessionId, existing, draft));
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Debug session not found or invalid");
        }

        try {
            var ctx = RuntimeContext.builder()
                    .userId(DEBUG_USER)
                    .sessionId(sessionId)
                    .build();
            // The synchronous debug API has no human-in-the-loop confirmation round trip. The
            // configured policy therefore decides whether tool calls run or stop for approval.
            session.agent.setPermissionMode(
                    ctx, PermissionMode.valueOf(draft.getPermissionMode().name()));

            ensureSession(sessionId, draft, request.message());
            recordTurn(sessionId, "user", request.message(), null, null);

            // Accumulate the final reply from text deltas (the demo's approach). Some models /
            // transports emit the answer only via TextBlockDeltaEvent and leave the final
            // AgentResultEvent text empty, especially across multi-turn tool calls.
            var answer = new StringBuilder();
            var finalMsg = new AtomicReference<Msg>();
            var toolCalled = new AtomicBoolean(false);
            var toolResultSeen = new AtomicBoolean(false);

            var started = Instant.now();
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
            var latencyMs = (int) java.time.Duration.between(started, Instant.now()).toMillis();

            // Prefer the streamed text; fall back to the result message's text content.
            String text = answer.toString();
            if (text.isBlank() && finalMsg.get() != null) {
                text = finalMsg.get().getTextContent();
            }

            String reply;
            if (text != null && !text.isBlank()) {
                reply = text.trim();
            } else if (toolCalled.get() && !toolResultSeen.get()) {
                reply = "The model decided to call a tool but the call did not complete. Check that"
                        + " the MCP server is reachable and that the model supports function calling.";
            } else if (toolResultSeen.get()) {
                reply = "The tool was called and returned a result, but the model did not produce a"
                        + " final answer. This usually means the selected model does not fully support"
                        + " multi-turn tool calling; try a model with confirmed tool-calling support.";
            } else {
                reply = "The model returned an empty response without calling any tool. Try rephrasing"
                        + " your message or select a different model.";
            }

            if (text == null || text.isBlank()) {
                log.warn(
                        "Debug chat empty; toolCalled={} toolResultSeen={} finalMsg={}",
                        toolCalled.get(),
                        toolResultSeen.get(),
                        finalMsg.get() != null);
                recordTurn(sessionId, "error", reply, null, latencyMs);
            } else {
                recordTurn(sessionId, "assistant", reply, null, latencyMs);
            }
            touchSession(sessionId);
            return new AgentChatResponse(sessionId, reply);
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
        purgeSession(sessionId);
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
    private Session resolveSession(String sessionId, Session existing, AgentAsset draft) {
        if (existing != null
                && existing.agentId.equals(draft.getId())
                && existing.configChangedAt.equals(draft.getUpdatedAt())) {
            return existing;
        }
        if (existing != null) {
            closeQuietly(existing.agent);
            // Config changed under the same session id: drop the stale persisted state so the
            // rebuilt agent starts from a clean memory/transcript.
            purgeSession(sessionId);
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

    private void ensureSession(String sessionId, AgentAsset draft, String firstUserMessage) {
        if (dialogue.sessionExists(sessionId)) {
            return;
        }
        var title = (firstUserMessage == null || firstUserMessage.isBlank())
                ? draft.getName()
                : (firstUserMessage.length() <= 50
                        ? firstUserMessage
                        : firstUserMessage.substring(0, 50) + "...");
        dialogue.ensureSession(sessionId, draft.getId(), DEBUG_USER, title);
    }

    private void recordTurn(
            String sessionId, String role, String content, String model, Integer latencyMs) {
        dialogue.recordMessage(sessionId, role, content, model, latencyMs);
    }

    private void touchSession(String sessionId) {
        dialogue.touchSession(sessionId);
    }

    private void purgeSession(String sessionId) {
        stateStore.delete(DEBUG_USER, sessionId);
        transcriptStore.deleteBySessionId(sessionId);
        dialogue.purge(sessionId);
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
