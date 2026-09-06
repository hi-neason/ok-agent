package io.okagent.module.channel.application.runtime;

import io.agentscope.harness.agent.gateway.GatewayBootstrap;
import io.okagent.module.channel.domain.ChannelAsset;
import io.okagent.module.channel.domain.ChannelRuntimeStatus;
import io.okagent.module.channel.infrastructure.persistence.ChannelAssetRepository;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Owns the live framework channel instances. On application readiness it starts every enabled
 * channel; on {@link ChannelRuntimeEvent} it incrementally reconciles a single channel (stop the
 * old bootstrap, then build and start a fresh one when still enabled and bound to an agent).
 */
@Component
public class ChannelRuntimeManager {

    private static final Logger log = LoggerFactory.getLogger(ChannelRuntimeManager.class);

    private final ChannelAssetRepository repository;
    private final ChannelGatewayFactory gatewayFactory;
    private final ChannelRuntimeStatusWriter statusWriter;
    private final Map<UUID, Object> channelLocks = new ConcurrentHashMap<>();
    private volatile boolean stopping;
    private final Map<UUID, GatewayBootstrap> live = new ConcurrentHashMap<>();

    public ChannelRuntimeManager(
            ChannelAssetRepository repository,
            ChannelGatewayFactory gatewayFactory,
            ChannelRuntimeStatusWriter statusWriter) {
        this.repository = repository;
        this.gatewayFactory = gatewayFactory;
        this.statusWriter = statusWriter;
    }

    /** Starts all enabled channels once the application context is ready. */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        for (ChannelAsset asset : repository.findByEnabledTrue()) {
            reconcile(asset.getId());
        }
    }

    /** Reconciles a single channel after its configuration transaction has committed. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChannelChanged(ChannelRuntimeEvent event) {
        if (event.deleted()) {
            synchronized (channelLock(event.channelId())) { stopLive(event.channelId()); }
            return;
        }
        reconcile(event.channelId());
    }

    @PreDestroy
    public void shutdown() {
        stopping = true;
        for (Map.Entry<UUID, GatewayBootstrap> entry : live.entrySet()) {
            synchronized (channelLock(entry.getKey())) { stopLive(entry.getKey()); }
        }
        live.clear();
    }

    private Object channelLock(UUID channelId) {
        // Locks survive channel deletion so concurrent old/new events cannot use different monitors.
        return channelLocks.computeIfAbsent(channelId, ignored -> new Object());
    }

    private void reconcile(UUID channelId) {
        synchronized (channelLock(channelId)) {
            if (!stopping) reconcileLocked(channelId);
        }
    }

    private void reconcileLocked(UUID channelId) {
        stopLive(channelId);
        ChannelAsset asset = repository.findById(channelId).orElse(null);
        if (asset == null || !asset.isEnabled()) {
            statusWriter.write(channelId, ChannelRuntimeStatus.STOPPED, null);
            return;
        }
        if (asset.getBoundAgentId() == null) {
            statusWriter.write(channelId, ChannelRuntimeStatus.ERROR, "No agent bound to this channel");
            log.warn("Channel '{}' has no bound agent; not starting", asset.getChannelKey());
            return;
        }
        statusWriter.write(channelId, ChannelRuntimeStatus.STARTING, null);
        try {
            GatewayBootstrap bootstrap = gatewayFactory.build(asset);
            bootstrap.start();
            if (stopping) { bootstrap.stop(); return; }
            live.put(channelId, bootstrap);
            statusWriter.write(channelId, ChannelRuntimeStatus.RUNNING, null);
            log.info("Channel '{}' started and bound to agent {}", asset.getChannelKey(), asset.getBoundAgentId());
        } catch (Exception e) {
            // For WeChat iLink, "not logged in" is an expected idle state (awaiting QR scan),
            // not a runtime failure — surface it as STOPPED rather than ERROR.
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (asset.getType() == io.okagent.module.channel.domain.ChannelType.WECHAT
                    && msg.contains("not logged in")) {
                statusWriter.write(channelId, ChannelRuntimeStatus.STOPPED, "等待微信扫码登录");
                log.info("Channel '{}' idle: {} ({}); build message: {}", asset.getChannelKey(), asset.getType(), e.getMessage());
                return;
            }
            log.warn("Failed to start channel '{}': {}", asset.getChannelKey(), e.getMessage(), e);
            statusWriter.write(channelId, ChannelRuntimeStatus.ERROR, e.getMessage());
        }
    }

    private void stopLive(UUID channelId) {
        GatewayBootstrap bootstrap = live.remove(channelId);
        if (bootstrap != null) {
            stopQuietly(channelId, bootstrap);
        }
    }

    private void stopQuietly(UUID channelId, GatewayBootstrap bootstrap) {
        try {
            bootstrap.stop();
        } catch (Exception e) {
            log.warn("Error stopping channel {}: {}", channelId, e.getMessage(), e);
        }
    }
}
