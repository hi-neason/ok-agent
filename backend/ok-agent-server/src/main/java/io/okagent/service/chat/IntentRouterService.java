package io.okagent.service.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.harness.agent.HarnessAgent;
import io.okagent.domain.agent.AgentAsset;
import io.okagent.domain.dialogue.DialogueSession;
import io.okagent.infrastructure.store.JdbcAgentStateStore;
import io.okagent.repository.agent.AgentAssetRepository;
import io.okagent.repository.model.ModelAssetRepository;
import io.okagent.service.agent.HarnessAgentFactory;
import io.okagent.service.dialogue.DialogueService;
import io.okagent.service.intent.IntentClassification;
import io.okagent.service.intent.IntentDto;
import io.okagent.service.intent.IntentNode;
import io.okagent.service.intent.IntentService;
import io.okagent.service.model.ApiKeyCipher;
import io.okagent.service.observe.TraceCollectingMiddleware;
import io.okagent.service.persona.PersonaExtractionService;
import io.okagent.web.chat.ProductionChatRequest;
import io.okagent.web.chat.ProductionChatResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

/**
 * Production chat entry point for the intent-routed, multi-agent customer-service topology.
 *
 * <p>Flow per message: (1) an LLM classifier maps the query onto the intent tree and returns the
 * matched {@code intentKey} + confidence; (2) the matched intentKey is reverse-resolved against
 * the router agent's {@code subagents_json} — the sub-agent whose declared {@code intentKeys}
 * contains it is the delegate; (3) when a delegate is found, a routing directive is prepended to
 * the user message and the router agent (which has the sub-agents configured) streams the reply,
 * delegating to the chosen sub-agent internally via agent_spawn/agent_send. When no sub-agent
 * claims the intent (or confidence is low), the query falls through to the router agent directly.
 *
 * <p>This deliberately does NOT reuse the debug controller: production traffic is identified by
 * (channel, session) and is routed, not manually selected by an agentId in the URL.
 */
@Service
public class IntentRouterService {
    private static final Logger log = LoggerFactory.getLogger(IntentRouterService.class);
    // Agentic loops can involve multiple LLM round-trips (router decision → sub-agent execution
    // → result synthesis). 120s is too tight for the whole stream once a sub-agent is spawned,
    // so give the overall turn a generous budget; per-call HTTP/model timeouts still bound
    // individual requests.
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(300);
    private static final double CONFIDENCE_FALLBACK = 0.6;
    private static final int MAX_SESSIONS = 200;

    private final IntentService intents;
    private final AgentAssetRepository agents;
    private final ModelAssetRepository models;
    private final ApiKeyCipher cipher;
    private final HarnessAgentFactory factory;
    private final DialogueService dialogue;
    private final JdbcAgentStateStore stateStore;
    private final PersonaExtractionService personaExtraction;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public IntentRouterService(
            IntentService intents,
            AgentAssetRepository agents,
            ModelAssetRepository models,
            ApiKeyCipher cipher,
            HarnessAgentFactory factory,
            DialogueService dialogue,
            JdbcAgentStateStore stateStore,
            PersonaExtractionService personaExtraction) {
        this.intents = intents;
        this.agents = agents;
        this.models = models;
        this.cipher = cipher;
        this.factory = factory;
        this.dialogue = dialogue;
        this.stateStore = stateStore;
        this.personaExtraction = personaExtraction;
    }

    public ProductionChatResponse chat(ProductionChatRequest req) {
        if (req.message() == null || req.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        var draft = agents.findById(req.agentId()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Router agent not found"));
        if (draft.getModelAssetId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "该路由智能体尚未配置模型，请先选择模型");
        }

        var userId = req.userId();
        var sessionKey = deriveSessionKey(req.channelId(), req.sessionId());
        var session = sessions.compute(sessionKey, (k, ex) -> resolveSession(k, ex, draft, userId));
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found or invalid");
        }

        // 1) Classify the query against the intent tree.
        var classification = classify(req.message(), draft);
        // 2) Build the user turn (with an optional routing directive for the router agent).
        var turnMessage = buildRoutedMessage(req.message(), classification);

        try {
            String traceId = UUID.randomUUID().toString().replace("-", "");
            int turnSeq = dialogue.nextSeq(sessionKey);
            var ctx = RuntimeContext.builder()
                    .userId(userId)
                    .sessionId(sessionKey)
                    .put(TraceCollectingMiddleware.CTX_TRACE_ID, traceId)
                    .put(TraceCollectingMiddleware.CTX_TURN_SEQ, turnSeq)
                    .put(TraceCollectingMiddleware.CTX_AGENT_ID, draft.getId().toString())
                    .build();
            session.agent.setPermissionMode(
                    ctx, PermissionMode.valueOf(draft.getPermissionMode().name()));

            ensureSession(sessionKey, draft, turnMessage, userId);
            recordTurn(sessionKey, "user", req.message(), null, null, traceId);

            var answer = new StringBuilder();
            var finalMsg = new AtomicReference<Msg>();
            var toolCalled = new AtomicBoolean(false);
            var toolResultSeen = new AtomicBoolean(false);
            var started = Instant.now();
            session.agent.streamEvents(turnMessage, ctx)
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
            var latencyMs = (int) Duration.between(started, Instant.now()).toMillis();

            // Prefer AgentResultEvent text over accumulated TextBlockDeltaEvent content.
            // When sub-agent delegation occurs, the sub-agent's text deltas also flow into
            // the parent event stream, so naive accumulation duplicates the content — the
            // sub-agent's reply followed by the parent's synthesis. AgentResultEvent carries
            // only the main agent's final synthesized result.
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
                reply = "模型决定调用工具但调用未完成，请检查 MCP 服务是否可达。";
            } else if (toolResultSeen.get()) {
                reply = "工具已返回结果，但模型未生成最终答复，请尝试支持多轮工具调用的模型。";
            } else {
                reply = "模型返回空答复，请换个说法或选择其它模型。";
            }

            if (text == null || text.isBlank()) {
                recordTurn(sessionKey, "error", reply, null, latencyMs, traceId);
            } else {
                recordTurn(sessionKey, "assistant", reply, null, latencyMs, traceId);
            }
            touchSession(sessionKey);
            personaExtraction.extractAsync(draft.getId(), userId, sessionKey);
            return new ProductionChatResponse(
                    sessionKey,
                    reply,
                    classification.intentKey(),
                    classification.intentName(),
                    classification.confidence(),
                    classification.targetSubagentKey(),
                    classification.fallback());
        } catch (Exception e) {
            var unwrapped = Exceptions.unwrap(e);
            log.warn("Production chat failed: {}", unwrapped.getMessage(), unwrapped);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, unwrapped.getMessage());
        }
    }

    /**
     * Classifies a query onto the intent tree using the router agent's own model, then
     * reverse-resolves the matched intentKey against the router's {@code subagents_json} to find
     * which sub-agent claims it. Best-effort: any failure yields a fallback classification with
     * no delegate so the router agent handles the query directly.
     */
    private IntentClassification classify(String query, AgentAsset router) {
        List<IntentDto> flat = flatten(intents.getTree());
        if (flat.isEmpty()) {
            return new IntentClassification(null, null, 0.0, null, true);
        }
        UUID modelId = router.getModelAssetId();
        if (modelId == null) {
            return new IntentClassification(null, null, 0.0, null, true);
        }
        var model = models.findById(modelId).filter(m -> m.isEnabled()).orElse(null);
        if (model == null) {
            return new IntentClassification(null, null, 0.0, null, true);
        }
        String raw;
        try {
            raw = callLlm(model, buildClassificationPrompt(query, flat));
        } catch (Exception e) {
            log.warn("Intent classification LLM call failed: {}", e.getMessage());
            return new IntentClassification(null, null, 0.0, null, true);
        }
        if (raw == null || raw.isBlank()) {
            return new IntentClassification(null, null, 0.0, null, true);
        }
        IntentDto matched;
        double confidence;
        try {
            JsonNode node = json.readTree(stripCodeFences(raw));
            String key = node.path("intentKey").asText("").trim();
            confidence = node.path("confidence").asDouble(0.0);
            matched = flat.stream()
                    .filter(i -> i.intentKey().equals(key))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Failed to parse intent classification: {}", e.getMessage());
            return new IntentClassification(null, null, 0.0, null, true);
        }
        if (matched == null || confidence < CONFIDENCE_FALLBACK) {
            return new IntentClassification(
                    matched != null ? matched.intentKey() : null,
                    matched != null ? matched.name() : null,
                    confidence,
                    null,
                    true);
        }
        // Reverse-resolve: which sub-agent declared responsibility for this intentKey?
        String delegate = resolveDelegate(router, matched.intentKey());
        if (delegate == null) {
            // Intent recognised but no sub-agent claims it → fall back to the router itself.
            return new IntentClassification(matched.intentKey(), matched.name(), confidence, null, true);
        }
        return new IntentClassification(matched.intentKey(), matched.name(), confidence, delegate, false);
    }

    /**
     * Finds the referenced sub-agent (its {@code agentKey}) declared on the router agent whose
     * {@code intentKeys} array contains the given intentKey. Each subagents_json entry is
     * {@code {"agentId": ..., "intentKeys": [...]}}; we load the target AgentAsset to resolve the
     * key the harness exposes to agent_spawn. Returns null when no reference claims the intent.
     */
    private String resolveDelegate(AgentAsset router, String intentKey) {
        String raw = router.getSubagentsJson();
        if (raw == null || raw.isBlank() || "[]".equals(raw.trim())) {
            return null;
        }
        List<Map<String, Object>> defs;
        try {
            defs = json.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("subagents_json is not a valid JSON array for agent={}", router.getAgentKey());
            return null;
        }
        for (Map<String, Object> def : defs) {
            Object keys = def.get("intentKeys");
            if (!(keys instanceof List<?> list)) continue;
            boolean claimed = false;
            for (Object k : list) {
                if (k != null && intentKey.equals(String.valueOf(k).trim())) {
                    claimed = true;
                    break;
                }
            }
            if (!claimed) continue;
            String agentId = asText(def.get("agentId"));
            if (agentId.isBlank()) continue;
            try {
                var child = agents.findById(UUID.fromString(agentId)).orElse(null);
                if (child != null && child.isEnabled() && child.getAgentKey() != null
                        && !child.getAgentKey().isBlank()) {
                    return child.getAgentKey();
                }
            } catch (IllegalArgumentException e) {
                log.warn("Sub-agent reference has invalid agentId '{}' on router={}",
                        agentId, router.getAgentKey());
            }
        }
        return null;
    }

    private static String asText(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private String buildRoutedMessage(String query, IntentClassification c) {
        if (c.fallback() || c.targetSubagentKey() == null || c.targetSubagentKey().isBlank()) {
            return query;
        }
        return String.format(
                """
                [路由指令] 用户意图已确定为「%s」(意图键: %s)，该意图由子Agent「%s」专门负责。

                你必须立即调用 agent_spawn 工具，将 agent_id 设为「%s」、task 设为下方用户原问题，将任务委派给该子Agent处理。
                不要自己回答，不要解释，直接调用 agent_spawn 并等待其返回结果，然后将子Agent的回复原样转达给用户。

                用户原问题：%s""",
                c.intentName() == null ? c.intentKey() : c.intentName(),
                c.intentKey(),
                c.targetSubagentKey(),
                c.targetSubagentKey(),
                query);
    }

    private String buildClassificationPrompt(String query, List<IntentDto> flat) {
        var sb = new StringBuilder();
        sb.append("你是一个客服意图分类器。下面是可用的意图树（意图键 | 名称 | 描述）：\n");
        for (var i : flat) {
            sb.append("- ").append(i.intentKey()).append(" | ").append(i.name()).append(" | ")
                    .append(i.description() == null ? "" : i.description()).append('\n');
        }
        sb.append("\n用户问题：").append(query).append('\n');
        sb.append("请只输出一个 JSON 对象：{\"intentKey\":\"最匹配的意图键，无匹配则空字符串\","
                + "\"confidence\":0.0到1.0之间的小数,\"reason\":\"简短理由\"}。"
                + "只能从上面列出的意图键中选择，不要编造。");
        return sb.toString();
    }

    private String callLlm(io.okagent.domain.model.ModelAsset model, String prompt) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model.getModelId());
            body.put("temperature", 0.0);
            body.put("max_tokens", 500);
            body.put("response_format", Map.of("type", "json_object"));
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", "你是严谨的意图分类助手，只输出 JSON。"),
                    Map.of("role", "user", "content", prompt));
            body.put("messages", messages);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(chatCompletionsUrl(model.getEndpoint())))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cipher.decrypt(model.getApiKeyCiphertext()))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Intent classification LLM returned HTTP {}", resp.statusCode());
                return null;
            }
            JsonNode root = json.readTree(resp.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) return null;
            return choices.get(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.warn("Intent classification call failed: {}", e.getMessage());
            return null;
        }
    }

    private static String chatCompletionsUrl(String endpoint) {
        String base = endpoint == null ? "" : endpoint.trim();
        if (base.endsWith("/chat/completions")) return base;
        if (base.endsWith("/")) return base + "chat/completions";
        return base + "/chat/completions";
    }

    private static String stripCodeFences(String raw) {
        String s = raw.strip();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) s = s.substring(firstNl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        }
        return s.strip();
    }

    private static List<IntentDto> flatten(List<IntentNode> nodes) {
        List<IntentDto> out = new ArrayList<>();
        for (var n : nodes) {
            out.add(n.node());
            out.addAll(flatten(n.children()));
        }
        return out;
    }

    private String deriveSessionKey(String channelId, String sessionId) {
        String sid = (sessionId == null || sessionId.isBlank()) ? "ps-" + UUID.randomUUID() : sessionId.trim();
        String ch = (channelId == null || channelId.isBlank()) ? "default" : channelId.trim();
        return ch + "::" + sid;
    }

    private Session resolveSession(String key, Session existing, AgentAsset draft, String userId) {
        if (existing != null
                && existing.agentId.equals(draft.getId())
                && existing.configChangedAt.equals(draft.getUpdatedAt())
                && java.util.Objects.equals(existing.userId, userId)) {
            return existing;
        }
        if (existing != null) {
            closeQuietly(existing.agent);
            purgeSession(key, existing.userId);
        }
        evictIfFull();
        return new Session(draft.getId(), draft.getUpdatedAt(), factory.build(draft, userId), userId);
    }

    private void evictIfFull() {
        if (sessions.size() < MAX_SESSIONS) return;
        sessions.entrySet().stream()
                .min(Map.Entry.comparingByValue((a, b) -> a.lastTouched.compareTo(b.lastTouched)))
                .ifPresent(e -> {
                    sessions.remove(e.getKey());
                    closeQuietly(e.getValue().agent);
                });
    }

    private void closeQuietly(HarnessAgent agent) {
        try {
            agent.close();
        } catch (Exception ignored) {
            // best effort
        }
    }

    private void ensureSession(String key, AgentAsset draft, String firstMessage, String userId) {
        if (dialogue.sessionExists(key)) return;
        var title = (firstMessage == null || firstMessage.isBlank())
                ? draft.getName()
                : (firstMessage.length() <= 50 ? firstMessage : firstMessage.substring(0, 50) + "...");
        dialogue.ensureSession(key, draft.getId(), userId, title);
    }

    private void recordTurn(String key, String role, String content, String model, Integer latencyMs, String traceId) {
        dialogue.recordMessage(key, role, content, model, latencyMs, traceId);
    }

    private void touchSession(String key) {
        dialogue.touchSession(key);
    }

    private void purgeSession(String key, String userId) {
        String effectiveUserId = userId;
        if (effectiveUserId == null) {
            effectiveUserId = dialogue.findById(key)
                    .map(DialogueSession::getUserId)
                    .orElse(null);
        }
        stateStore.delete(effectiveUserId, key);
        dialogue.purge(key);
    }

    private static final class Session {
        private final UUID agentId;
        private final Instant configChangedAt;
        private final HarnessAgent agent;
        private final String userId;
        private final Instant lastTouched = Instant.now();

        private Session(UUID agentId, Instant configChangedAt, HarnessAgent agent, String userId) {
            this.agentId = agentId;
            this.configChangedAt = configChangedAt;
            this.agent = agent;
            this.userId = userId;
        }
    }
}
