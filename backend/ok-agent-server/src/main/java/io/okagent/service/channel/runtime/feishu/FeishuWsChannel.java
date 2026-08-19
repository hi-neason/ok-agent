package io.okagent.service.channel.runtime.feishu;

import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.ws.Client;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.extensions.channel.common.BotLoopGuard;
import io.agentscope.extensions.channel.common.IdempotencyStore;
import io.agentscope.harness.agent.gateway.Gateway;
import io.agentscope.harness.agent.gateway.SessionIdUtils;
import io.agentscope.harness.agent.gateway.channel.Channel;
import io.agentscope.harness.agent.gateway.channel.ChannelConfig;
import io.agentscope.harness.agent.gateway.channel.ChannelRouter;
import io.agentscope.harness.agent.gateway.channel.InboundMessage;
import io.agentscope.harness.agent.gateway.channel.OutboundAddress;
import io.okagent.service.dialogue.DialogueService;
import io.okagent.service.observe.TraceCollectingMiddleware;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Feishu channel backed by the official SDK's long-connection (WebSocket) client. Unlike the
 * agentscope-provided {@code FeishuChannel} (HTTP webhook only), this adapter requires no public
 * callback URL: the SDK establishes an outbound WebSocket to Feishu and receives {@code
 * im.message.receive_v1} events over it.
 *
 * <p>Lifecycle:
 *
 * <ol>
 *   <li>{@link #init(Gateway)} stores the auto-wired gateway
 *   <li>{@link #start()} builds a {@link com.lark.oapi.ws.Client} and runs its blocking {@code
 *       start()} on a dedicated daemon thread (with auto-reconnect)
 *   <li>{@link #stop()} closes the WebSocket and releases the thread
 * </ol>
 *
 * <p>Inbound events are de-duplicated by event id, guarded against bot loops, mapped to {@link
 * InboundMessage}, routed through {@link ChannelRouter}, and dispatched to the gateway. The reply
 * is sent back to the originating chat via {@link FeishuOutboundSender}.
 */
public final class FeishuWsChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(FeishuWsChannel.class);
    /** Feishu requires handlers to process within ~3s or it retries; dispatch is async so we ack
     * immediately and run the agent turn off the websocket thread. */
    private static final long CONNECT_TIMEOUT_MS = 15_000L;

    private final String channelId;
    private final ChannelConfig config;
    private final String appId;
    private final String appSecret;
    private final FeishuOutboundSender sender;
    private final FeishuEventMapper mapper;
    private final IdempotencyStore idempotency = new IdempotencyStore();
    private final BotLoopGuard botLoopGuard = new BotLoopGuard();
    private final ChannelRouter router;
    private final DialogueService dialogue;
    private final UUID agentId;
    private final String agentName;

    private volatile Gateway gateway;
    private volatile Client wsClient;
    private volatile ExecutorService wsRunner;

    public FeishuWsChannel(
            String channelId,
            ChannelConfig config,
            String appId,
            String appSecret,
            com.lark.oapi.Client apiClient,
            DialogueService dialogue,
            UUID agentId,
            String agentName) {
        this.channelId = Objects.requireNonNull(channelId, "channelId");
        this.config = Objects.requireNonNull(config, "config");
        this.appId = Objects.requireNonNull(appId, "appId");
        this.appSecret = Objects.requireNonNull(appSecret, "appSecret");
        this.sender = new FeishuOutboundSender(Objects.requireNonNull(apiClient, "apiClient"));
        this.mapper = new FeishuEventMapper(channelId);
        this.router = new ChannelRouter(config.defaultAgentId());
        this.dialogue = Objects.requireNonNull(dialogue, "dialogue");
        this.agentId = Objects.requireNonNull(agentId, "agentId");
        this.agentName = agentName;
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
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    @Override
    public synchronized void start() {
        if (wsClient != null) {
            return;
        }
        EventDispatcher dispatcher = EventDispatcher.newBuilder("", "")
                .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                    @Override
                    public void handle(P2MessageReceiveV1 event) {
                        onEvent(event);
                    }
                })
                .build();
        Client client = new Client.Builder(appId, appSecret)
                .eventHandler(dispatcher)
                .autoReconnect(true)
                .build();
        this.wsClient = client;

        // Client.start() blocks for the lifetime of the connection, so run it on a dedicated
        // daemon thread. The SDK handles its own reconnect loop internally.
        ExecutorService runner = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "feishu-ws-" + channelId);
            t.setDaemon(true);
            return t;
        });
        this.wsRunner = runner;
        runner.execute(() -> {
            try {
                client.start();
            } catch (Throwable t) {
                if (!isShuttingDown()) {
                    log.warn("Feishu WS client for channel '{}' ended: {}", channelId, t.getMessage());
                }
            }
        });

        try {
            client.awaitReady(CONNECT_TIMEOUT_MS);
            log.info("Feishu long-connection channel '{}' connected (appId={})", channelId, appId);
        } catch (Exception e) {
            log.warn(
                    "Feishu long-connection channel '{}' not ready within {}ms: {}",
                    channelId,
                    CONNECT_TIMEOUT_MS,
                    e.getMessage());
        }
    }

    @Override
    public synchronized void stop() {
        Client client = this.wsClient;
        this.wsClient = null;
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.debug("Error closing Feishu WS client '{}': {}", channelId, e.getMessage());
            }
        }
        ExecutorService runner = this.wsRunner;
        this.wsRunner = null;
        if (runner != null) {
            runner.shutdownNow();
            try {
                runner.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Feishu long-connection channel '{}' stopped", channelId);
    }

    @Override
    public Mono<Msg> dispatch(InboundMessage message) {
        Gateway g = gateway;
        if (g == null) {
            return Mono.error(new IllegalStateException("FeishuWsChannel '" + channelId + "' has no gateway"));
        }
        var route = router.resolveRoute(config, message);
        String sessionId = businessSessionId(route.context().canonicalKey());
        String userId = message.senderId();
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
                                route.context(), message.messages(), route.outboundAddress(), runtimeCtx, message)
                        .flatMap(reply ->
                                sendReply(route.outboundAddress(), reply).thenReturn(reply))
                        .doOnNext(reply -> recordTurnEnd(sessionId, reply, started, null, traceId))
                        .doOnError(err -> recordTurnEnd(sessionId, null, started, err, traceId)));
    }

    /**
     * Deterministic business session id derived from the same canonical key the gateway uses to
     * scope its internal session, so channel turns are grouped consistently with DmScope.
     */
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
            log.warn("Feishu WS: failed to record user turn (session='{}'): {}", sessionId, e.getMessage());
        }
    }

    private void recordTurnEnd(String sessionId, Msg reply, Instant started, Throwable error, String traceId) {
        try {
            int latencyMs = (int) Duration.between(started, Instant.now()).toMillis();
            if (error != null) {
                dialogue.recordMessage(
                        sessionId, "error", "Agent 执行失败：" + error.getMessage(), null, latencyMs, traceId);
            } else if (reply != null) {
                String text = reply.getTextContent();
                if (text != null && !text.isBlank()) {
                    dialogue.recordMessage(sessionId, "assistant", text.trim(), null, latencyMs, traceId);
                }
            }
            dialogue.touchSession(sessionId);
        } catch (Exception e) {
            log.warn("Feishu WS: failed to record assistant turn (session='{}'): {}", sessionId, e.getMessage());
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

    @Override
    public Flux<AgentEvent> dispatchStream(InboundMessage message) {
        // Streaming replies are collapsed to the final Msg and sent as one text reply; the official
        // SDK does not expose message editing here, so incremental streaming is not supported.
        return Flux.from(dispatch(message)).flatMap(msg -> Flux.empty());
    }

    @Override
    public void deliver(OutboundAddress address, List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        String chatId = chatIdOf(address);
        if (chatId == null) {
            log.warn("Feishu deliver: cannot resolve chatId from address '{}'", address);
            return;
        }
        for (Msg msg : messages) {
            String text = msg.getTextContent();
            if (text != null && !text.isBlank()) {
                sender.sendText(chatId, text);
            }
        }
    }

    // -----------------------------------------------------------------
    //  Event handling
    // -----------------------------------------------------------------

    private void onEvent(P2MessageReceiveV1 event) {
        try {
            // Idempotency by event id (long-connection also retries on handler timeout).
            String eventId = event.getHeader() != null ? event.getHeader().getEventId() : null;
            if (eventId != null && !idempotency.firstSeen(channelId + "|" + eventId)) {
                return;
            }

            FeishuEventMapper.Mapped mapped = mapper.map(event).orElse(null);
            if (mapped == null) {
                return;
            }
            InboundMessage in = mapped.inbound();

            if (!botLoopGuard.allow(in.peer().key())) {
                log.debug(
                        "Feishu WS: bot-loop guard tripped for peer='{}' (channel='{}')",
                        in.peer().key(),
                        channelId);
                return;
            }

            // Run the agent turn asynchronously; the SDK handler returns immediately so we stay
            // well within Feishu's 3s ack window.
            dispatch(in)
                    .doOnError(err ->
                            log.warn("Feishu WS: agent run failed (channel='{}'): {}", channelId, err.getMessage()))
                    .subscribe();
        } catch (Throwable t) {
            log.warn("Feishu WS: error handling event (channel='{}'): {}", channelId, t.getMessage(), t);
        }
    }

    private Mono<Void> sendReply(OutboundAddress address, Msg reply) {
        if (reply == null) {
            return Mono.empty();
        }
        String text = reply.getTextContent();
        if (text == null || text.isBlank()) {
            return Mono.empty();
        }
        String chatId = chatIdOf(address);
        return Mono.fromRunnable(() -> sender.sendText(chatId, text));
    }

    /** Parses the chat_id from an {@code "channelId:KIND:chatId"} outbound address. */
    private String chatIdOf(OutboundAddress address) {
        if (address == null || address.to() == null) {
            return null;
        }
        String to = address.to();
        int first = to.indexOf(':');
        if (first < 0) {
            return to;
        }
        int second = to.indexOf(':', first + 1);
        return second < 0 ? to.substring(first + 1) : to.substring(second + 1);
    }

    private boolean isShuttingDown() {
        return wsClient == null;
    }
}
