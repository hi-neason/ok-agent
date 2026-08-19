package io.okagent.service.channel.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.GatewayBootstrap;
import io.agentscope.harness.agent.gateway.channel.Channel;
import io.agentscope.harness.agent.gateway.channel.ChannelConfig;
import io.agentscope.harness.agent.gateway.channel.ChannelRuntimeContextRequest;
import io.agentscope.harness.agent.gateway.channel.ChannelRuntimeContextResolver;
import io.agentscope.harness.agent.gateway.channel.DmScope;
import io.okagent.domain.channel.ChannelAsset;
import io.okagent.domain.channel.ChannelDmScope;
import io.okagent.repository.agent.AgentAssetRepository;
import io.okagent.service.agent.HarnessAgentFactory;
import io.okagent.service.channel.ChannelUserService;
import io.okagent.service.channel.runtime.feishu.FeishuWsChannel;
import io.okagent.service.model.ApiKeyCipher;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Builds a framework {@link GatewayBootstrap} for one channel asset: constructs the provider
 * channel (Feishu) via the 2.0.2 SPI and binds the configured Agent built through
 * {@link HarnessAgentFactory}. Each live channel owns an isolated gateway/bootstrap.
 */
@Component
public class ChannelGatewayFactory {

    private static final Logger log = LoggerFactory.getLogger(ChannelGatewayFactory.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HarnessAgentFactory agentFactory;
    private final AgentAssetRepository agentRepository;
    private final ApiKeyCipher cipher;
    private final ChannelUserService channelUsers;

    public ChannelGatewayFactory(
            HarnessAgentFactory agentFactory,
            AgentAssetRepository agentRepository,
            ApiKeyCipher cipher,
            ChannelUserService channelUsers) {
        this.agentFactory = agentFactory;
        this.agentRepository = agentRepository;
        this.cipher = cipher;
        this.channelUsers = channelUsers;
    }

    /**
     * Builds (but does not start) a gateway bootstrap hosting the given channel and bound agent.
     */
    public GatewayBootstrap build(ChannelAsset asset) {
        if (asset.getType() != io.okagent.domain.channel.ChannelType.FEISHU) {
            throw new IllegalArgumentException("Unsupported channel type: " + asset.getType());
        }
        return buildFeishu(asset);
    }

    private GatewayBootstrap buildFeishu(ChannelAsset asset) {
        UUID agentId = asset.getBoundAgentId();
        if (agentId == null) {
            throw new IllegalStateException("Channel '" + asset.getChannelKey() + "' has no bound agent");
        }
        var agentAsset = agentRepository
                .findById(agentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Bound agent " + agentId + " for channel '" + asset.getChannelKey() + "' was not found"));
        HarnessAgent agent = agentFactory.build(agentAsset);
        String agentKey = agentAsset.getAgentKey();

        Map<String, Object> properties = feishuProperties(asset);
        String appId = asString(properties, "appId");
        String appSecret = asString(properties, "appSecret");
        if (appId == null || appId.isBlank()) {
            throw new IllegalStateException("Feishu channel '" + asset.getChannelKey() + "' is missing appId");
        }
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalStateException("Feishu channel '" + asset.getChannelKey() + "' is missing appSecret");
        }

        ChannelConfig routing = ChannelConfig.builder(asset.getChannelKey())
                .defaultAgentId(agentKey)
                .dmScope(toDmScope(asset.getDmScope()))
                .build();

        // Official SDK client used both for the WebSocket long connection (inbound) and the
        // Open API message send (outbound). No public callback URL is required.
        Client larkClient = Client.newBuilder(appId, appSecret).build();
        Channel channel = new FeishuWsChannel(asset.getChannelKey(), routing, appId, appSecret, larkClient);

        log.info(
                "Building Feishu long-connection channel '{}' bound to agent '{}' (dmScope={})",
                asset.getChannelKey(),
                agentKey,
                asset.getDmScope());

        return GatewayBootstrap.builder()
                .agent(agentKey, agent)
                .channel(channel)
                .runtimeContextResolver(inboundUserTracker(asset))
                .build();
    }

    /**
     * Returns a resolver that records the inbound sender as a channel user on every turn while
     * leaving the caller runtime context unchanged (the gateway still applies sessionId/userId from
     * the resolved MsgContext afterwards).
     */
    private ChannelRuntimeContextResolver inboundUserTracker(ChannelAsset asset) {
        return new ChannelRuntimeContextResolver() {
            @Override
            public RuntimeContext resolve(ChannelRuntimeContextRequest request) {
                try {
                    if (request != null && request.inboundMessage() != null) {
                        var inbound = request.inboundMessage();
                        channelUsers.recordInbound(
                                asset.getType().name(),
                                asset.getChannelKey(),
                                inbound.senderId(),
                                null,
                                inbound.accountId(),
                                inbound.senderId(),
                                null);
                    }
                } catch (Exception e) {
                    log.debug("Channel user tracking skipped: {}", e.getMessage());
                }
                return null;
            }
        };
    }

    private Map<String, Object> feishuProperties(ChannelAsset asset) {
        Map<String, Object> props = new LinkedHashMap<>(readMap(asset.getConfigJson()));
        Map<String, String> secrets = readSecrets(asset.getSecretsCiphertext());
        copyIfPresent(props, secrets, "appSecret");
        copyIfPresent(props, secrets, "encryptKey");
        copyIfPresent(props, secrets, "verificationToken");
        return props;
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse channel config", e);
        }
    }

    private Map<String, String> readSecrets(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(cipher.decrypt(ciphertext), new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt channel secrets", e);
        }
    }

    private void copyIfPresent(Map<String, Object> target, Map<String, String> source, String key) {
        String value = source.get(key);
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static String asString(Map<String, Object> props, String key) {
        Object v = props.get(key);
        return v == null ? null : v.toString();
    }

    private DmScope toDmScope(ChannelDmScope scope) {
        if (scope == null) {
            return DmScope.PER_PEER;
        }
        return switch (scope) {
            case MAIN -> DmScope.MAIN;
            case PER_PEER -> DmScope.PER_PEER;
            case PER_CHANNEL_PEER -> DmScope.PER_CHANNEL_PEER;
            case PER_ACCOUNT_CHANNEL_PEER -> DmScope.PER_ACCOUNT_CHANNEL_PEER;
        };
    }
}
