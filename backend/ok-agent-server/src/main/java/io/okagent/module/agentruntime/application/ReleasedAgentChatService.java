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
import io.okagent.module.channel.domain.ChannelAsset;
import io.okagent.module.customerchat.application.CustomerChatCommand;
import io.okagent.module.customerchat.application.CustomerChatResult;
import io.okagent.module.customerchat.application.CustomerChatService;
import io.okagent.module.channel.infrastructure.persistence.ChannelAssetRepository;
import io.okagent.module.agent.application.HarnessAgentFactory;
import io.okagent.module.agent.application.ResolvedAgentConfig;
import io.okagent.module.agent.application.ResolvedSubagent;
import io.okagent.module.conversation.application.DialogueService;
import io.okagent.module.intent.application.IntentClassification;
import io.okagent.module.observe.application.TraceCollectingMiddleware;
import io.okagent.module.persona.application.PersonaExtractionService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
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
 * <p>All production requests use the channel's validated immutable release. Draft execution is
 * available only through the separate Agent debug API.
 */
@Service
public class ReleasedAgentChatService implements CustomerChatService {
    private static final Logger log = LoggerFactory.getLogger(ReleasedAgentChatService.class);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(300);
    private static final double CONFIDENCE_FALLBACK = 0.6;
    private static final int MAX_SESSIONS = 200;

    private final io.okagent.module.release.infrastructure.persistence.AgentVersionRepository versions;
    private final io.okagent.module.release.application.ReleasedChannelAgentResolver releasedAgents;
    private final ChannelAssetRepository channels;


    private final HarnessAgentFactory factory;
    private final DialogueService dialogue;
    private final PersonaExtractionService personaExtraction;
    private final ObjectMapper json;
    private final SessionPool<HarnessAgent> sessions = new SessionPool<>(MAX_SESSIONS);

    public ReleasedAgentChatService(
            io.okagent.module.release.application.ReleasedChannelAgentResolver releasedAgents,
            ChannelAssetRepository channels,
            HarnessAgentFactory factory,
            DialogueService dialogue,
            PersonaExtractionService personaExtraction,
            ObjectMapper json,
            io.okagent.module.release.infrastructure.persistence.AgentVersionRepository versions) {
        this.versions = versions;
        this.releasedAgents = releasedAgents;
        this.channels = channels;
        this.factory = factory;
        this.dialogue = dialogue;
        this.personaExtraction = personaExtraction;
        this.json = json;
    }

    @Override
    public CustomerChatResult chat(CustomerChatCommand req) {
        if (req.message() == null || req.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        ResolvedRuntime runtime = resolveRuntime(req);
        var sessionAddress = deriveSessionAddress(req.channelId(), req.sessionId());
        runtime = pinRuntime(sessionAddress.storageKey(), runtime, req.userId());
        ResolvedAgentConfig cfg = runtime.config();
        if (cfg.getModelAssetId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该路由智能体尚未配置模型，请先选择模型");
        }

        var userId = req.userId();
        var sessionKey = sessionAddress.storageKey();
        String traceId = UUID.randomUUID().toString().replace("-", "");
        dialogue.assertSessionOwner(sessionKey, cfg.getId(), userId);
        try (var lease = sessions.acquire(sessionKey, cfg.contentHash(), () -> factory.build(cfg, userId))) {
            var agent = lease.value();
            ensureSession(sessionKey, cfg, runtime, req.message(), userId);
            int turnSeq = dialogue.nextSeq(sessionKey);
            recordTurn(sessionKey, "user", req.message(), null, null, traceId, runtime);
            requireAutomation(sessionKey);
            var classification = classify(req.message(), cfg);
            var turnMessage = buildRoutedMessage(req.message(), classification);
            var ctx = RuntimeContext.builder()
                    .userId(userId)
                    .sessionId(sessionKey)
                    .put(TraceCollectingMiddleware.CTX_TRACE_ID, traceId)
                    .put(TraceCollectingMiddleware.CTX_TURN_SEQ, turnSeq)
                    .put(TraceCollectingMiddleware.CTX_AGENT_ID, cfg.getId().toString())
                    .build();
            agent.setPermissionMode(
                    ctx, PermissionMode.valueOf(cfg.getPermissionMode().name()));


            var answer = new StringBuilder();
            var finalMsg = new AtomicReference<Msg>();
            var toolCalled = new AtomicBoolean(false);
            var toolResultSeen = new AtomicBoolean(false);
            var started = Instant.now();
            agent
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

            requireAutomation(sessionKey);
            if (text == null || text.isBlank()) {
                recordTurn(sessionKey, "error", reply, null, latencyMs, traceId, runtime);
            } else {
                recordTurn(sessionKey, "assistant", reply, null, latencyMs, traceId, runtime);
            }
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
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception e) {
            var unwrapped = Exceptions.unwrap(e);
            var failure = io.okagent.shared.runtime.RuntimeFailure.from(unwrapped, traceId);
            log.warn("Production chat failed agent={} session={} trace={} code={} causeType={}",
                    cfg.getId(), sessionKey, traceId, failure.code(), unwrapped.getClass().getSimpleName());
            throw failure;
        }
    }

    /** Resolves the runtime config and its release attribution for a production request. */
    ResolvedRuntime resolveRuntime(CustomerChatCommand req) {
        UUID channelId;
        try {
            channelId = UUID.fromString(req.channelId() == null ? "" : req.channelId().trim());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A published channel UUID is required");
        }
        ChannelAsset channel = channels.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
        if (!channel.isEnabled() || !java.util.Objects.equals(channel.getBoundAgentId(), req.agentId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Channel is disabled or agent binding does not match");
        }
        try {
            var released = releasedAgents.resolve(channel);
            return new ResolvedRuntime(released.config(), released.releaseId(), released.versionNo(), true);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Channel has no valid promoted release");
        }
    }

    ResolvedRuntime pinRuntime(String sessionKey, ResolvedRuntime current, String userId) {
        var existing = dialogue.findById(sessionKey).orElse(null);
        if (existing == null) return current;
        dialogue.assertSessionOwner(sessionKey, current.config().getId(), userId);
        if (existing.getVersionNo() == null || existing.getReleaseId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Legacy session has no pinned release; start a new session");
        }
        var version = versions.findByAgentIdAndVersionNo(existing.getAgentId(), existing.getVersionNo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Pinned session version is unavailable"));
        return new ResolvedRuntime(
                io.okagent.module.release.application.ReleaseAgentConfig.fromSnapshot(version.getSnapshotJson()),
                existing.getReleaseId(), existing.getVersionNo(), true);
    }

    record ResolvedRuntime(
            ResolvedAgentConfig config, UUID releaseId, Integer versionNo, boolean fromRelease) {}

    /**
     * Classifies a query against the intent tree, then reverse-resolves the matched intentKey against
     * the router's already-resolved sub-agents (each carries its declared intentKeys) to find the
     * delegate. Best-effort: any failure yields a fallback classification with no delegate.
     */
    private IntentClassification classify(String query, ResolvedAgentConfig router) {
        if (router.getSubagents() == null || router.getSubagents().isEmpty()) {
            return new IntentClassification(null, null, 0.0, null, true);
        }
        List<io.okagent.module.agent.application.ResolvedIntent> flat = router.getResolvedIntents();
        if (flat.isEmpty()) {
            return new IntentClassification(null, null, 0.0, null, true);
        }
        String raw;
        try {
            raw = factory.classify(router, buildClassificationPrompt(query, flat));
        } catch (Exception e) {
            log.warn("Intent classification failed agent={} causeType={}", router.getId(), e.getClass().getSimpleName());
            return new IntentClassification(null, null, 0.0, null, true);
        }
        if (raw == null || raw.isBlank()) {
            return new IntentClassification(null, null, 0.0, null, true);
        }
        io.okagent.module.agent.application.ResolvedIntent matched;
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
            log.warn("Invalid classification response agent={} causeType={}", router.getId(), e.getClass().getSimpleName());
            return new IntentClassification(null, null, 0.0, null, true);
        }
        if (matched == null || (!Double.isFinite(confidence) || confidence > 1.0 || confidence < CONFIDENCE_FALLBACK)) {
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

    private String buildClassificationPrompt(String query, List<io.okagent.module.agent.application.ResolvedIntent> flat) {
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

    private static String stripCodeFences(String raw) {
        String s = raw.strip();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) s = s.substring(firstNl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        }
        return s.strip();
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

    private void requireAutomation(String sessionKey) {
        if (!dialogue.allowsAutomation(sessionKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "HUMAN_HANDOFF_ACTIVE");
        }
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        sessions.close();
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

}
