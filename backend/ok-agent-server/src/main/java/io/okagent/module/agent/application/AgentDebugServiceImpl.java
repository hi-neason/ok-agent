package io.okagent.module.agent.application;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.harness.agent.HarnessAgent;
import io.okagent.module.agent.domain.AgentAsset;
import io.okagent.module.conversation.domain.DialogueSession;
import io.okagent.infrastructure.store.JdbcAgentStateStore;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.conversation.application.DialogueService;
import io.okagent.module.observe.application.TraceCollectingMiddleware;
import io.okagent.module.persona.application.PersonaExtractionService;
import io.okagent.module.agent.application.AgentChatRequest;
import io.okagent.module.agent.application.AgentChatResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.Exceptions;

@Service
public class AgentDebugServiceImpl implements AgentDebugService {
    private static final Logger log = LoggerFactory.getLogger(AgentDebugServiceImpl.class);
    // Generous budget for agentic loops that may involve sub-agent spawning and multiple
    // LLM round-trips. Individual model/HTTP calls are bounded by their own timeouts.
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(300);
    private static final int MAX_SESSIONS = 50;

    private final AgentAssetRepository agents;
    private final HarnessAgentFactory factory;
    private final DialogueService dialogue;
    private final JdbcAgentStateStore stateStore;
    private final PersonaExtractionService personaExtraction;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AgentDebugServiceImpl(
            AgentAssetRepository agents,
            HarnessAgentFactory factory,
            DialogueService dialogue,
            JdbcAgentStateStore stateStore,
            PersonaExtractionService personaExtraction) {
        this.agents = agents;
        this.factory = factory;
        this.dialogue = dialogue;
        this.stateStore = stateStore;
        this.personaExtraction = personaExtraction;
    }

    @Override
    public AgentChatResponse chat(UUID agentId, AgentChatRequest request) {
        var draft = loadDraft(agentId);
        if (draft.getModelAssetId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该智能体尚未配置模型，请先在配置中选择模型后再发起对话");
        }

        var userId = request.userId();
        var sessionId = resolveSessionId(request.sessionId());
        dialogue.assertSessionOwner(sessionId, draft.getId(), userId);
        var debugConfig = factory.draftConfig(draft);
        var session =
                sessions.compute(sessionId, (key, existing) -> resolveSession(sessionId, existing, draft, debugConfig, userId));
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Debug session not found or invalid");
        }
        if (!session.executionLock.tryLock()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Debug session is already processing a request");
        }

        try {
            String traceId = UUID.randomUUID().toString().replace("-", "");
            int turnSeq = dialogue.nextSeq(sessionId);
            var ctx = RuntimeContext.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .put(TraceCollectingMiddleware.CTX_TRACE_ID, traceId)
                    .put(TraceCollectingMiddleware.CTX_TURN_SEQ, turnSeq)
                    .put(TraceCollectingMiddleware.CTX_AGENT_ID, draft.getId().toString())
                    .build();
            // The synchronous debug API has no human-in-the-loop confirmation round trip. The
            // configured policy therefore decides whether tool calls run or stop for approval.
            session.agent.setPermissionMode(
                    ctx, PermissionMode.valueOf(draft.getPermissionMode().name()));

            ensureSession(sessionId, draft, request.message(), userId);
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
            var latencyMs =
                    (int) java.time.Duration.between(started, Instant.now()).toMillis();

            // Prefer AgentResultEvent text over accumulated deltas. Some models only emit
            // deltas and leave the result text empty, so we fall back to delta accumulation.
            // When sub-agent delegation occurs, deltas from the sub-agent also flow into the
            // parent stream, so naive accumulation would duplicate content — AgentResultEvent
            // carries only the main agent's final synthesized result.
            String text = null;
            if (finalMsg.get() != null) {
                String resultText = finalMsg.get().getTextContent();
                if (resultText != null && !resultText.isBlank()) {
                    text = resultText;
                }
            }
            if (text == null || text.isBlank()) {
                text = answer.toString();
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
                recordTurn(sessionId, "error", reply, null, latencyMs, traceId);
            } else {
                recordTurn(sessionId, "assistant", reply, null, latencyMs, traceId);
            }
            session.touch();
            touchSession(sessionId);
            // Best-effort: asynchronously extract/update the user's persona from this conversation.
            // Fire-and-forget; never affects the chat response.
            personaExtraction.extractAsync(draft.getId(), userId, sessionId);
            return new AgentChatResponse(sessionId, reply);
        } catch (Exception e) {
            throw toUserFacingError(e);
        } finally {
            session.executionLock.unlock();
        }
    }

    @Override
    public void resetSession(String sessionId, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        var active = sessions.get(sessionId);
        if (active != null && !java.util.Objects.equals(active.userId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session belongs to another user");
        }
        dialogue.findById(sessionId).ifPresent(existing -> {
            if (!java.util.Objects.equals(existing.getUserId(), userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session belongs to another user");
            }
        });
        var removed = active == null ? null : (sessions.remove(sessionId, active) ? active : null);
        if (removed != null) {
            closeQuietly(removed.agent);
        }
        purgeSession(sessionId, userId);
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
    private Session resolveSession(
            String sessionId, Session existing, AgentAsset draft, DraftAgentConfig config, String userId) {
        String configKey = config.contentHash();
        if (existing != null
                && existing.agentId.equals(draft.getId())
                && existing.configKey.equals(configKey)
                && java.util.Objects.equals(existing.userId, userId)) {
            return existing;
        }
        if (existing != null) {
            closeQuietly(existing.agent);
            // Config changed under the same session id: drop the stale persisted state so the
            // rebuilt agent starts from a clean memory/transcript.
            purgeSession(sessionId, existing.userId);
        }
        evictIfFull();
        return new Session(draft.getId(), configKey, factory.build(config, userId), userId);
    }

    private void evictIfFull() {
        if (sessions.size() < MAX_SESSIONS) {
            return;
        }
        sessions.entrySet().stream()
                .min(Map.Entry.comparingByValue((a, b) -> a.lastTouched.compareTo(b.lastTouched)))
                .ifPresent(entry -> {
                    if (sessions.remove(entry.getKey(), entry.getValue())) {
                        closeQuietly(entry.getValue().agent);
                    }
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
        } catch (Exception exception) {
            log.debug("Failed to close debug agent", exception);
        }
    }

    private void ensureSession(String sessionId, AgentAsset draft, String firstUserMessage, String userId) {
        if (dialogue.sessionExists(sessionId)) {
            return;
        }
        var title = (firstUserMessage == null || firstUserMessage.isBlank())
                ? draft.getName()
                : (firstUserMessage.length() <= 50 ? firstUserMessage : firstUserMessage.substring(0, 50) + "...");
        dialogue.ensureSession(sessionId, draft.getId(), userId, title);
    }

    private void recordTurn(String sessionId, String role, String content, String model, Integer latencyMs) {
        dialogue.recordMessage(sessionId, role, content, model, latencyMs, null);
    }

    private void recordTurn(
            String sessionId, String role, String content, String model, Integer latencyMs, String traceId) {
        dialogue.recordMessage(sessionId, role, content, model, latencyMs, traceId);
    }

    private void touchSession(String sessionId) {
        dialogue.touchSession(sessionId);
    }

    private void purgeSession(String sessionId, String userId) {
        String effectiveUserId = userId;
        if (effectiveUserId == null) {
            effectiveUserId =
                    dialogue.findById(sessionId).map(DialogueSession::getUserId).orElse(null);
        }
        stateStore.delete(effectiveUserId, sessionId);
        dialogue.purge(sessionId);
    }

    private static final class Session {
        private final UUID agentId;
        private final String configKey;
        private final HarnessAgent agent;
        private final String userId;
        private volatile Instant lastTouched = Instant.now();
        private final ReentrantLock executionLock = new ReentrantLock();

        private Session(UUID agentId, String configKey, HarnessAgent agent, String userId) {
            this.agentId = agentId;
            this.configKey = configKey;
            this.agent = agent;
            this.userId = userId;
        }

        private void touch() {
            lastTouched = Instant.now();
        }
    }
}
