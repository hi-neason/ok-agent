package io.okagent.module.channel.application.runtime.wechat;

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
import io.agentscope.harness.agent.gateway.channel.Peer;
import io.agentscope.harness.agent.gateway.channel.PeerKind;
import io.okagent.module.channel.domain.ChannelIlinkSession;
import io.okagent.module.channel.infrastructure.persistence.ChannelIlinkSessionRepository;
import io.okagent.module.channel.application.ChannelIdentityResolver;
import io.okagent.module.conversation.application.DialogueService;
import io.okagent.module.observe.application.TraceCollectingMiddleware;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * WeChat iLink (ClawBot) channel backed by long-polling over HTTP. Unlike the Feishu WebSocket
 * channel, iLink has no push connection: a daemon thread loops {@code getupdates} (the server holds
 * each request ~35s), maps inbound text messages to the gateway, and replies through {@code
 * sendmessage} carrying the inbound {@code context_token}.
 *
 * <p>The bot_token comes from the {@link ChannelIlinkSession} obtained via QR login; when it is
 * absent the channel stays stopped until a scan completes. iLink is 1v1-only (the bot cannot join
 * group chats), so every peer is a {@link PeerKind#DIRECT}.
 */
public final class WeChatIlinkChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(WeChatIlinkChannel.class);

    private static final long RECONNECT_BASE_MS = 1_000L;
    private static final long RECONNECT_MAX_MS = 60_000L;

    private final UUID channelDbId;
    private final String channelId;
    private final ChannelConfig config;
    private final IlinkClient client;
    private final String botToken;
    private final ChannelIlinkSessionRepository sessions;
    private final TransactionTemplate tx;
    private final ChannelRouter router;
    private final IdempotencyStore idempotency = new IdempotencyStore();
    private final BotLoopGuard botLoopGuard = new BotLoopGuard();
    private final DialogueService dialogue;
    private final ChannelIdentityResolver identityResolver;
    private final UUID agentId;
    private final String agentName;

    private volatile Gateway gateway;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ExecutorService poller;
    /** Latest reply token per WeChat user id, required by iLink to route a reply. */
    private final Map<String, String> contextByUser = new ConcurrentHashMap<>();

    public WeChatIlinkChannel(
            UUID channelDbId,
            String channelId,
            ChannelConfig config,
            IlinkClient client,
            String botToken,
            ChannelIlinkSessionRepository sessions,
            TransactionTemplate tx,
            DialogueService dialogue,
            ChannelIdentityResolver identityResolver,
            UUID agentId,
            String agentName) {
        this.channelDbId = Objects.requireNonNull(channelDbId, "channelDbId");
        this.channelId = Objects.requireNonNull(channelId, "channelId");
        this.config = Objects.requireNonNull(config, "config");
        this.client = Objects.requireNonNull(client, "client");
        this.botToken = botToken;
        this.sessions = sessions;
        this.tx = tx;
        this.router = new ChannelRouter(config.defaultAgentId());
        this.dialogue = Objects.requireNonNull(dialogue, "dialogue");
        this.identityResolver = Objects.requireNonNull(identityResolver, "identityResolver");
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
        if (running.get()) {
            return;
        }
        if (botToken == null || botToken.isBlank()) {
            log.info("WeChat iLink channel '{}' not started: no bot_token (awaiting QR login)", channelId);
            return;
        }
        running.set(true);
        ExecutorService runner = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "wechat-ilink-" + channelId);
            t.setDaemon(true);
            return t;
        });
        this.poller = runner;
        runner.execute(this::pollLoop);
        log.info("WeChat iLink channel '{}' long-poll started", channelId);
    }

    @Override
    public synchronized void stop() {
        running.set(false);
        ExecutorService runner = this.poller;
        this.poller = null;
        if (runner != null) {
            runner.shutdownNow();
            try {
                runner.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        contextByUser.clear();
        log.info("WeChat iLink channel '{}' stopped", channelId);
    }

    // -----------------------------------------------------------------
    //  Long-poll loop
    // -----------------------------------------------------------------

    private void pollLoop() {
        String cursor = loadCursor();
        long backoff = RECONNECT_BASE_MS;
        while (running.get()) {
            try {
                IlinkClient.UpdateBatch batch = client.getUpdates(botToken, cursor);
                backoff = RECONNECT_BASE_MS;
                cursor = batch.nextCursor();
                persistCursor(cursor);
                for (IlinkClient.IncomingMessage m : batch.messages()) {
                    handleIncoming(m);
                }
            } catch (Exception e) {
                if (!running.get()) {
                    break;
                }
                log.warn("WeChat iLink channel '{}' poll error: {}", channelId, e.getMessage(), e);
                sleep(backoff);
                backoff = Math.min(backoff * 2, RECONNECT_MAX_MS);
            }
        }
    }

    private void handleIncoming(IlinkClient.IncomingMessage m) {
        try {
            if (m.messageId() != null && !idempotency.firstSeen(channelId + "|" + m.messageId())) {
                return;
            }
            String from = m.fromUserId();
            if (from == null || from.isBlank()) {
                return;
            }
            // iLink only supports 1v1 chats.
            Peer peer = new Peer(PeerKind.DIRECT, from);
            if (!botLoopGuard.allow(peer.key())) {
                return;
            }
            if (m.contextToken() != null && !m.contextToken().isBlank()) {
                contextByUser.put(from, m.contextToken());
            }
            String userId = identityResolver.resolve(
                    "WECHAT", channelId, from, null, null, from, null);
            String text = m.text() == null ? "" : m.text().trim();
            if (text.isEmpty()) {
                return;
            }
            Msg msg = Msg.builder().role(io.agentscope.core.message.MsgRole.USER).name(from).textContent(text).build();
            InboundMessage inbound = InboundMessage.builder(channelId, peer, List.of(msg))
                    .senderId(from)
                    .build();

            dispatch(inbound).subscribe();
        } catch (Throwable t) {
            log.warn("WeChat iLink channel '{}' handle error: {}", channelId, t.getMessage(), t);
        }
    }

    // -----------------------------------------------------------------
    //  Dispatch (mirrors FeishuWsChannel: dialogue record + trace + identity)
    // -----------------------------------------------------------------

    @Override
    public Mono<Msg> dispatch(InboundMessage in) {
        Gateway g = gateway;
        if (g == null) {
            return Mono.error(new IllegalStateException("WeChat iLink channel '" + channelId + "' has no gateway"));
        }
        String from = in.senderId();
        String userId = identityResolver.resolve(
                "WECHAT", channelId, from, null, null, from, null);
        String sessionId = businessSessionId(in.peer().key());
        String userText = firstText(in);
        var route = router.resolveRoute(config, in);
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
                .flatMap(started -> g.run(route.context(), in.messages(), route.outboundAddress(), runtimeCtx, in)
                        .flatMap(reply -> sendReply(from, reply).thenReturn(reply))
                        .doOnNext(reply -> recordTurnEnd(sessionId, reply, started, null, traceId))
                        .doOnError(err -> recordTurnEnd(sessionId, null, started, err, traceId)));
    }

    @Override
    public Flux<AgentEvent> dispatchStream(InboundMessage message) {
        // Streaming replies are collapsed to the final Msg and sent as one text reply; iLink has no
        // message-edit/streaming surface.
        return Flux.from(dispatch(message)).flatMap(msg -> Flux.empty());
    }

    @Override
    public void deliver(OutboundAddress address, List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        String userId = userIdOf(address);
        if (userId == null) {
            return;
        }
        for (Msg msg : messages) {
            String text = msg.getTextContent();
            if (text != null && !text.isBlank()) {
                sendRaw(userId, text);
            }
        }
    }

    private Mono<Void> sendReply(String toUserId, Msg reply) {
        if (reply == null) {
            return Mono.empty();
        }
        String text = reply.getTextContent();
        if (text == null || text.isBlank()) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> sendRaw(toUserId, text.trim()));
    }

    private void sendRaw(String toUserId, String text) {
        String contextToken = contextByUser.get(toUserId);
        try {
            client.sendText(botToken, toUserId, contextToken, text);
        } catch (Exception e) {
            log.warn("WeChat iLink send to {} failed: {}", toUserId, e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------
    //  Session / cursor / dialogue
    // -----------------------------------------------------------------

    private String businessSessionId(String canonicalKey) {
        return "ch-" + SessionIdUtils.deterministicHash(channelId, canonicalKey);
    }

    private String loadCursor() {
        if (sessions == null) {
            return "";
        }
        try {
            return tx.execute(status ->
                    sessions.findByChannelId(channelDbId).map(ChannelIlinkSession::getPollCursor).orElse(""));
        } catch (Exception e) {
            log.debug("WeChat iLink load cursor failed: {}", e.getMessage());
            return "";
        }
    }

    private void persistCursor(String cursor) {
        if (sessions == null || cursor == null) {
            return;
        }
        try {
            tx.executeWithoutResult(status -> sessions.updateCursor(channelDbId, cursor, java.time.Instant.now()));
        } catch (Exception e) {
            log.debug("WeChat iLink persist cursor failed: {}", e.getMessage());
        }
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
            log.warn("WeChat iLink: failed to record user turn (session='{}'): {}", sessionId, e.getMessage(), e);
        }
    }

    private void recordTurnEnd(
            String sessionId, Msg reply, Instant started, Throwable error, String traceId) {
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
            log.warn("WeChat iLink: failed to record assistant turn (session='{}'): {}", sessionId, e.getMessage(), e);
        }
    }

    private String userIdOf(OutboundAddress address) {
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

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
