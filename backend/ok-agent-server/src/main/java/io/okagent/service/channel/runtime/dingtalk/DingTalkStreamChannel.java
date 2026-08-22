package io.okagent.service.channel.runtime.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.extensions.channel.common.BotLoopGuard;
import io.agentscope.extensions.channel.common.IdempotencyStore;
import io.agentscope.extensions.channel.dingtalk.DingTalkAccessTokenProvider;
import io.agentscope.extensions.channel.dingtalk.DingTalkChannelProperties;
import io.agentscope.extensions.channel.dingtalk.DingTalkInboundMapper;
import io.agentscope.extensions.channel.dingtalk.DingTalkOutboundClient;
import io.agentscope.extensions.channel.dingtalk.DingTalkStreamClient;
import io.agentscope.harness.agent.gateway.Gateway;
import io.agentscope.harness.agent.gateway.SessionIdUtils;
import io.agentscope.harness.agent.gateway.channel.Channel;
import io.agentscope.harness.agent.gateway.channel.ChannelConfig;
import io.agentscope.harness.agent.gateway.channel.ChannelRouter;
import io.agentscope.harness.agent.gateway.channel.InboundMessage;
import io.agentscope.harness.agent.gateway.channel.OutboundAddress;
import io.okagent.service.channel.ChannelIdentityResolver;
import io.okagent.service.dialogue.DialogueService;
import io.okagent.service.observe.TraceCollectingMiddleware;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * DingTalk channel wrapper ("包一层") around the agentscope-provided Stream protocol clients.
 *
 * <p>We deliberately do <b>not</b> use {@code io.agentscope...dingtalk.DingTalkChannel} directly:
 * this wrapper owns the same {@link DingTalkStreamClient} / {@link DingTalkInboundMapper} /
 * {@link DingTalkOutboundClient} building blocks but inserts our business cross-cutting concerns
 * around {@link #dispatch(InboundMessage)} — dialogue persistence, unified user-id resolution via
 * {@link ChannelIdentityResolver}, and trace context — exactly like {@code FeishuWsChannel} does.
 *
 * <p>The stream client runs on its own daemon thread with exponential-backoff reconnect, so
 * {@link #start()} / {@link #stop()} just delegate lifecycle.
 */
public final class DingTalkStreamChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(DingTalkStreamChannel.class);

    private final String channelId;
    private final ChannelConfig config;
    private final DingTalkChannelProperties properties;
    private final DingTalkInboundMapper mapper;
    private final DingTalkOutboundClient outboundClient;
    private final IdempotencyStore idempotency = new IdempotencyStore();
    private final BotLoopGuard botLoopGuard = new BotLoopGuard();
    private final ChannelRouter router;
    private final DialogueService dialogue;
    private final ChannelIdentityResolver identityResolver;
    private final UUID agentId;
    private final String agentName;
    private final String channelType;
    private final DingTalkStreamClient streamClient;

    private volatile Gateway gateway;

    public DingTalkStreamChannel(
            String channelId,
            ChannelConfig config,
            DingTalkChannelProperties properties,
            DialogueService dialogue,
            ChannelIdentityResolver identityResolver,
            UUID agentId,
            String agentName,
            String channelType) {
        this.channelId = Objects.requireNonNull(channelId, "channelId");
        this.config = Objects.requireNonNull(config, "config");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.dialogue = Objects.requireNonNull(dialogue, "dialogue");
        this.identityResolver = Objects.requireNonNull(identityResolver, "identityResolver");
        this.agentId = Objects.requireNonNull(agentId, "agentId");
        this.agentName = agentName;
        this.channelType = channelType;
        this.router = new ChannelRouter(config.defaultAgentId());
        this.mapper = new DingTalkInboundMapper(channelId, properties.appKey());
        DingTalkAccessTokenProvider tokenProvider =
                new DingTalkAccessTokenProvider(properties.apiBase(), properties.appKey(), properties.appSecret());
        this.outboundClient =
                new DingTalkOutboundClient(properties.apiBase(), tokenProvider, properties.robotCode());
        this.streamClient = new DingTalkStreamClient(properties, this::onInboundPayload);
    }

    @Override
    public String channelId() {
        return channelId;
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public void init(Gateway gateway) {
        if (this.gateway == null) {
            this.gateway = Objects.requireNonNull(gateway, "gateway");
        }
    }

    @Override
    public void start() {
        streamClient.start();
        log.info(
                "DingTalk channel '{}' started: appKey={}, robotCode={}",
                channelId,
                properties.appKey(),
                properties.robotCode());
    }

    @Override
    public void stop() {
        streamClient.stop();
        log.info("DingTalk channel '{}' stopped", channelId);
    }

    @Override
    public Mono<Msg> dispatch(InboundMessage message) {
        Gateway g = gateway;
        if (g == null) {
            return Mono.error(new IllegalStateException(
                    "DingTalkStreamChannel '" + channelId + "' has no gateway"));
        }
        var route = router.resolveRoute(config, message);
        String sessionId = businessSessionId(route.context().canonicalKey());
        String externalId = message.senderId();
        String userId = identityResolver.resolve(
                channelType, channelId, externalId, null, message.accountId(), externalId, null);
        String userText = firstText(message);
        String traceId = UUID.randomUUID().toString().replace("-", "");
        int turnSeq = dialogue.nextSeq(sessionId);
        RuntimeContext runtimeCtx = RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .put(TraceCollectingMiddleware.CTX_TRACE_ID, traceId)
                .put(TraceCollectingMiddleware.CTX_TURN_SEQ, turnSeq)
                .put(TraceCollectingMiddleware.CTX_AGENT_ID, agentId.toString())
                .build();

        return Mono.fromRunnable(() -> recordTurnStart(sessionId, userId, userText))
                .then(Mono.fromCallable(Instant::now))
                .flatMap(started -> g.run(
                                route.context(),
                                message.messages(),
                                route.outboundAddress(),
                                runtimeCtx,
                                message)
                        .flatMap(reply -> outboundClient
                                .send(route.outboundAddress(), List.of(reply))
                                .thenReturn(reply))
                        .doOnNext(reply -> recordTurnEnd(sessionId, reply, started, null, traceId))
                        .doOnError(err -> recordTurnEnd(sessionId, null, started, err, traceId)));
    }

    @Override
    public Flux<AgentEvent> dispatchStream(InboundMessage message) {
        // DingTalk OpenAPI replies are whole-message sends; collapse streaming to the final Msg.
        return Flux.from(dispatch(message)).flatMap(msg -> Flux.empty());
    }

    @Override
    public void deliver(OutboundAddress address, List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        outboundClient
                .send(address, messages)
                .doOnError(err -> log.warn(
                        "DingTalk channel '{}' deliver failed: {}",
                        channelId,
                        err.getMessage()))
                .subscribe();
    }

    // -----------------------------------------------------------------
    //  Stream callback
    // -----------------------------------------------------------------

    private void onInboundPayload(JsonNode payload) {
        try {
            var msgIdOpt = DingTalkInboundMapper.extractMsgId(payload);
            if (msgIdOpt.isPresent() && !idempotency.firstSeen(channelId + "|" + msgIdOpt.get())) {
                log.debug(
                        "DingTalk dispatch: duplicate msgId={} (channelId='{}')",
                        msgIdOpt.get(),
                        channelId);
                return;
            }
            InboundMessage in = mapper.map(payload).orElse(null);
            if (in == null) {
                return;
            }
            if (!botLoopGuard.allow(in.peer().key())) {
                log.warn(
                        "DingTalk dispatch: bot-loop guard tripped for peer='{}' (channelId='{}')",
                        in.peer().key(),
                        channelId);
                return;
            }
            // Async so the stream ACK thread returns immediately; the agent turn runs off-stream.
            dispatch(in)
                    .doOnError(err -> log.warn(
                            "DingTalk channel '{}' dispatch failed: {}",
                            channelId,
                            err.getMessage()))
                    .subscribe();
        } catch (Throwable t) {
            log.warn("DingTalk channel '{}' error handling payload: {}", channelId, t.getMessage(), t);
        }
    }

    private String businessSessionId(String canonicalKey) {
        return "ch-" + SessionIdUtils.deterministicHash(channelId, canonicalKey);
    }

    private void recordTurnStart(String sessionId, String userId, String userText) {
        try {
            if (!dialogue.sessionExists(sessionId)) {
                String title = (userText == null || userText.isBlank())
                        ? agentName
                        : (userText.length() <= 50 ? userText : userText.substring(0, 50) + "...");
                dialogue.ensureSession(sessionId, agentId, userId, title);
            }
            if (userText != null && !userText.isBlank()) {
                dialogue.recordMessage(sessionId, "user", userText, null, null);
            }
        } catch (Exception e) {
            log.warn("DingTalk: failed to record user turn (session='{}'): {}", sessionId, e.getMessage());
        }
    }

    private void recordTurnEnd(
            String sessionId, Msg reply, Instant started, Throwable error, String traceId) {
        try {
            int latencyMs = (int) Duration.between(started, Instant.now()).toMillis();
            if (error != null) {
                dialogue.recordMessage(
                        sessionId,
                        "error",
                        "Agent 执行失败：" + error.getMessage(),
                        null,
                        latencyMs,
                        traceId);
            } else if (reply != null) {
                String text = reply.getTextContent();
                if (text != null && !text.isBlank()) {
                    dialogue.recordMessage(sessionId, "assistant", text.trim(), null, latencyMs, traceId);
                }
            }
            dialogue.touchSession(sessionId);
        } catch (Exception e) {
            log.warn("DingTalk: failed to record assistant turn (session='{}'): {}", sessionId, e.getMessage());
        }
    }

    private static String firstText(InboundMessage message) {
        if (message.messages() == null) {
            return null;
        }
        return message.messages().stream()
                .map(Msg::getTextContent)
                .filter(t -> t != null && !t.isBlank())
                .findFirst()
                .orElse(null);
    }
}
