package io.okagent.module.agentruntime.application;

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
import io.okagent.domain.channel.ChannelAsset;
import io.okagent.domain.dialogue.DialogueSession;
import io.okagent.domain.release.AgentRelease;
import io.okagent.domain.release.AgentVersion;
import io.okagent.infrastructure.store.JdbcAgentStateStore;
import io.okagent.module.customerchat.application.CustomerChatCommand;
import io.okagent.module.customerchat.application.CustomerChatResult;
import io.okagent.module.customerchat.application.CustomerChatService;
import io.okagent.repository.agent.AgentAssetRepository;
import io.okagent.repository.channel.ChannelAssetRepository;
import io.okagent.repository.model.ModelAssetRepository;
import io.okagent.repository.release.AgentReleaseRepository;
import io.okagent.repository.release.AgentVersionRepository;
import io.okagent.service.agent.HarnessAgentFactory;
import io.okagent.service.agent.ResolvedAgentConfig;
import io.okagent.service.agent.ResolvedSubagent;
import io.okagent.service.dialogue.DialogueService;
import io.okagent.service.intent.IntentClassification;
import io.okagent.service.intent.IntentDto;
import io.okagent.service.intent.IntentNode;
import io.okagent.service.intent.IntentService;
import io.okagent.service.model.ApiKeyCipher;
import io.okagent.service.observe.TraceCollectingMiddleware;
import io.okagent.service.persona.PersonaExtractionService;
import io.okagent.service.release.ReleaseAgentConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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

/**
 * Production chat entry point for the intent-routed, multi-agent customer-service topology.
 *
 * <p>Production traffic is resolved from a channel's currently-promoted release: the channel points
 * at an {@link AgentRelease}, which points at an immutable {@link AgentVersion} whose snapshot is
 * built into a {@link HarnessAgent}. The runtime never reads the editable draft for a published
 * channel. If a channel has no release yet the service falls back to the draft (for pre-go-lucky
 * testing), logging a warning so the gap is visible.
 */
@Service
public class ReleasedAgentChatService implements CustomerChatService {
    private static final Logger log = LoggerFactory.getLogger(ReleasedAgentChatService.class);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(300);
    private static final double CONFIDENCE_FALLBACK = 0.6;
    private static final int MAX_SESSIONS = 200;

    private final IntentService intents;
    private final AgentAssetRepository agents;
    private final ChannelAssetRepository channels;
    private final AgentReleaseRepository releases;
    private final AgentVersionRepository versions;
    private final ModelAssetRepository models;
    private final ApiKeyCipher cipher;
    private final HarnessAgentFactory factory;
    private final DialogueService dialogue;
    private final JdbcAgentStateStore stateStore;
    private final PersonaExtractionService personaExtraction;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public ReleasedAgentChatService(
            IntentService intents,
            AgentAssetRepository agents,
            ChannelAssetRepository channels,
            AgentReleaseRepository releases,
            AgentVersionRepository versions,
            ModelAssetRepository models,
            ApiKeyCipher cipher,
            HarnessAgentFactory factory,
            DialogueService dialogue,
            JdbcAgentStateStore stateStore,
            PersonaExtractionService personaExtraction) {
        this.intents = intents;
        this.agents = agents;
        this.channels = channels;
        this.releases = releases;
        this.versions = versions;
        this.models = models;
        this.cipher = cipher;
        this.factory = factory;
        this.dialogue = dialogue;
        this.stateStore = stateStore;
        this.personaExtraction = personaExtraction;
    }

    @Override
    public CustomerChatResult chat(CustomerChatCommand req) {
        if (req.message() == null || req.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        AgentAsset draft = agents.findById(req.agentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Router agent not found"));

        // Resolve the runtime config: a published channel runs its release snapshot; otherwise fall
        // back to the draft (pre-go-live testing). The resolved config also carries the release id
        // for observability attribution.
        ResolvedRuntime runtime = resolveRuntime(req, draft);
        ResolvedAgentConfig cfg = runtime.config();
        if (cfg.getModelAssetId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该路由智能体尚未配置模型，请先选择模型");
        }

        var userId = req.userId();
        var sessionAddress = deriveSessionAddress(req.channelId(), req.sessionId());
        var sessionKey = sessionAddress.storageKey();
        dialogue.assertSessionOwner(sessionKey, cfg.getId(), userId);
        var session = sessions.compute(sessionKey, (k, ex) -> resolveSession(k, ex, cfg, userId));
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found or invalid");
        }
        if (!session.executionLock.tryLock()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is already processing a request");
        }

        try {
            var classification = classify(req.message(), cfg);
            var turnMessage = buildRoutedMessage(req.message(), classification);
            String traceId = UUID.randomUUID().toString().replace("-", "");
            int turnSeq = dialogue.nextSeq(sessionKey);
            var ctx = RuntimeContext.builder()
                    .userId(userId)
                    .sessionId(sessionKey)
                    .put(TraceCollectingMiddleware.CTX_TRACE_ID, traceId)
                    .put(TraceCollectingMiddleware.CTX_TURN_SEQ, turnSeq)
                    .put(TraceCollectingMiddleware.CTX_AGENT_ID, cfg.getId().toString())
                    .build();
            session.agent.setPermissionMode(
                    ctx, PermissionMode.valueOf(cfg.getPermissionMode().name()));

            ensureSession(sessionKey, cfg, runtime, turnMessage, userId);
            recordTurn(sessionKey, "user", req.message(), null, null, traceId, runtime);

            var answer = new StringBuilder();
            var finalMsg = new AtomicReference<Msg>();
            var toolCalled = new AtomicBoolean(false);
            var toolResultSeen = new AtomicBoolean(false);
            var started = Instant.now();
            session.agent
                    .streamEvents(turnMessage, ctx)
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
                recordTurn(sessionKey, "error", reply, null, latencyMs, traceId, runtime);
            } else {
                recordTurn(sessionKey, "assistant", reply, null, latencyMs, traceId, runtime);
            }
            session.touch();
            touchSession(sessionKey);
            personaExtraction.extractAsync(cfg.getId(), userId, sessionKey);
            return new CustomerChatResult(
                    sessionAddress.sessionId(),
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
        } finally {
            session.executionLock.unlock();
        }
    }

    /** Resolves the runtime config and its release attribution for a production request. */
    private ResolvedRuntime resolveRuntime(CustomerChatCommand req, AgentAsset draft) {
        if (req.channelId() != null && !req.channelId().isBlank()) {
            try {
                UUID channelId = UUID.fromString(req.channelId().trim());
                ChannelAsset channel = channels.findById(channelId).orElse(null);
                if (channel != null && channel.getCurrentReleaseId() != null) {
                    AgentRelease release =
                            releases.findById(channel.getCurrentReleaseId()).orElse(null);
                    if (release != null) {
                        AgentVersion version = versions.findById(release.getVersionId()).orElse(null);
                        if (version != null) {
                            return new ResolvedRuntime(
                                    ReleaseAgentConfig.fromSnapshot(version.getSnapshotJson()),
                                    release.getId(),
                                    version.getVersionNo(),
                                    true);
                        }
                    }
                    log.warn(
                            "Channel {} has current_release_id={} but the release/version is missing; falling back to draft",
                            channelId,
                            channel.getCurrentReleaseId());
                }
            } catch (IllegalArgumentException e) {
                log.warn("Production request has non-UUID channelId '{}'; using draft", req.channelId());
            }
        }
        // No published release on this channel (or no channel): build from draft so pre-go-live
        // testing keeps working. This path should disappear once every channel is published.
        log.info("No release for channel={}; serving draft for agent={}", req.channelId(), draft.getAgentKey());
        return new ResolvedRuntime(factory.draftConfig(draft), null, null, false);
    }

    private record ResolvedRuntime(
            ResolvedAgentConfig config, UUID releaseId, Integer versionNo, boolean fromRelease) {}

    /**
     * Classifies a query against the intent tree, then reverse-resolves the matched intentKey against
     * the router's already-resolved sub-agents (each carries its declared intentKeys) to find the
     * delegate. Best-effort: any failure yields a fallback classification with no delegate.
     */
    private IntentClassification classify(String query, ResolvedAgentConfig router) {
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
        String delegate = resolveDelegate(router, matched.intentKey());
        if (delegate == null) {
            return new IntentClassification(matched.intentKey(), matched.name(), confidence, null, true);
        }
        return new IntentClassification(matched.intentKey(), matched.name(), confidence, delegate, false);
    }

    /** Finds the resolved sub-agent whose declared intentKeys contain the intentKey, returning its agentKey. */
    private String resolveDelegate(ResolvedAgentConfig router, String intentKey) {
        List<ResolvedSubagent> subs = router.getSubagents();
        if (subs == null) return null;
        for (ResolvedSubagent sub : subs) {
            if (sub.intentKeys() == null) continue;
            for (String k : sub.intentKeys()) {
                if (intentKey.equals(k)) {
                    String key = sub.config().getAgentKey();
                    return (key == null || key.isBlank()) ? null : key;
                }
            }
        }
        return null;
    }

    private String buildRoutedMessage(String query, IntentClassification c) {
        if (c.fallback()
                || c.targetSubagentKey() == null
                || c.targetSubagentKey().isBlank()) {
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
            sb.append("- ")
                    .append(i.intentKey())
                    .append(" | ")
                    .append(i.name())
                    .append(" | ")
                    .append(i.description() == null ? "" : i.description())
                    .append('\n');
        }
        sb.append("\n用户问题：").append(query).append('\n');
        sb.append("请只输出一个 JSON 对象：{\"intentKey\":\"最匹配的意图键，无匹配则空字符串\","
                + "\"confidence\":0.0到1.0之间的小数,\"reason\":\"简短理由\"}。"
                + "只能从上面列出的意图键中选择，不要编造。");
        return sb.toString();
    }

    private String callLlm(io.okagent.domain.model.ModelAsset model, String prompt) {
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
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

    static SessionAddress deriveSessionAddress(String channelId, String sessionId) {
        String sid = (sessionId == null || sessionId.isBlank()) ? "ps-" + UUID.randomUUID() : sessionId.trim();
        String ch = (channelId == null || channelId.isBlank()) ? "default" : channelId.trim();
        String combined = ch + "::" + sid;
        String storageKey = combined.length() <= 64 && !ch.contains("::") && !sid.contains("::")
                ? combined
                : "ps-" + base64Sha256(ch + "\0" + sid);
        return new SessionAddress(sid, storageKey);
    }

    private static String base64Sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record SessionAddress(String sessionId, String storageKey) {}

    private Session resolveSession(String key, Session existing, ResolvedAgentConfig cfg, String userId) {
        if (existing != null
                && existing.agentId.equals(cfg.getId())
                && existing.configKey.equals(cfg.contentHash())
                && java.util.Objects.equals(existing.userId, userId)) {
            return existing;
        }
        if (existing != null) {
            closeQuietly(existing.agent);
            purgeSession(key, existing.userId);
        }
        evictIfFull();
        return new Session(cfg.getId(), cfg.contentHash(), factory.build(cfg, userId), userId);
    }

    private void evictIfFull() {
        if (sessions.size() < MAX_SESSIONS) return;
        sessions.entrySet().stream()
                .min(Map.Entry.comparingByValue((a, b) -> a.lastTouched.compareTo(b.lastTouched)))
                .ifPresent(e -> {
                    if (sessions.remove(e.getKey(), e.getValue())) {
                        closeQuietly(e.getValue().agent);
                    }
                });
    }

    private void closeQuietly(HarnessAgent agent) {
        try {
            agent.close();
        } catch (Exception ignored) {
            // best effort
        }
    }

    private void ensureSession(
            String key, ResolvedAgentConfig cfg, ResolvedRuntime runtime, String firstMessage, String userId) {
        if (dialogue.sessionExists(key)) return;
        var title = (firstMessage == null || firstMessage.isBlank())
                ? cfg.getName()
                : (firstMessage.length() <= 50 ? firstMessage : firstMessage.substring(0, 50) + "...");
        dialogue.ensureSession(key, cfg.getId(), runtime.releaseId(), runtime.versionNo(), userId, title);
    }

    private void recordTurn(
            String key,
            String role,
            String content,
            String model,
            Integer latencyMs,
            String traceId,
            ResolvedRuntime runtime) {
        // Release/version is attributed at session level (it is constant for a session and a new
        // release rebuilds the session via contentHash), so turns do not duplicate it here.
        dialogue.recordMessage(key, role, content, model, latencyMs, traceId);
    }

    private void touchSession(String key) {
        dialogue.touchSession(key);
    }

    private void purgeSession(String key, String userId) {
        String effectiveUserId = userId;
        if (effectiveUserId == null) {
            effectiveUserId =
                    dialogue.findById(key).map(DialogueSession::getUserId).orElse(null);
        }
        stateStore.delete(effectiveUserId, key);
        dialogue.purge(key);
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
