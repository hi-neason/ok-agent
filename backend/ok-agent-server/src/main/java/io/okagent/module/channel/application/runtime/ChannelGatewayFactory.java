package io.okagent.module.channel.application.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import io.agentscope.extensions.channel.dingtalk.DingTalkChannelProperties;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.GatewayBootstrap;
import io.agentscope.harness.agent.gateway.channel.Channel;
import io.agentscope.harness.agent.gateway.channel.ChannelConfig;
import io.agentscope.harness.agent.gateway.channel.DmScope;
import io.okagent.module.channel.domain.ChannelAsset;
import io.okagent.module.channel.domain.ChannelDmScope;
import io.okagent.module.channel.domain.ChannelIlinkSession;
import io.okagent.module.channel.domain.IlinkLoginStatus;
import io.okagent.module.channel.infrastructure.persistence.ChannelIlinkSessionRepository;
import io.okagent.module.agent.application.HarnessAgentFactory;
import io.okagent.module.channel.application.ChannelIdentityResolver;
import io.okagent.module.channel.application.runtime.dingtalk.DingTalkStreamChannel;
import io.okagent.module.channel.application.runtime.feishu.FeishuWsChannel;
import io.okagent.module.channel.application.runtime.wechat.IlinkClient;
import io.okagent.module.channel.application.runtime.wechat.WeChatIlinkChannel;
import io.okagent.module.conversation.application.DialogueService;
import io.okagent.module.model.application.ApiKeyCipher;
import io.okagent.module.release.application.ReleasedChannelAgent;
import io.okagent.module.release.application.ReleasedChannelAgentResolver;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final ReleasedChannelAgentResolver releasedAgents;
    private final ApiKeyCipher cipher;
    private final ChannelIdentityResolver identityResolver;
    private final DialogueService dialogue;
    private final ChannelIlinkSessionRepository ilinkSessions;
    private final TransactionTemplate tx;

    public ChannelGatewayFactory(
            HarnessAgentFactory agentFactory,
            ReleasedChannelAgentResolver releasedAgents,
            ApiKeyCipher cipher,
            ChannelIdentityResolver identityResolver,
            DialogueService dialogue,
            ChannelIlinkSessionRepository ilinkSessions,
            TransactionTemplate tx) {
        this.agentFactory = agentFactory;
        this.releasedAgents = releasedAgents;
        this.cipher = cipher;
        this.identityResolver = identityResolver;
        this.dialogue = dialogue;
        this.ilinkSessions = ilinkSessions;
        this.tx = tx;
    }

    /**
     * Builds (but does not start) a gateway bootstrap hosting the given channel and bound agent.
     */
    public GatewayBootstrap build(ChannelAsset asset) {
        return switch (asset.getType()) {
            case FEISHU -> buildFeishu(asset);
            case WECHAT -> buildWechatIlink(asset);
            case DINGTALK -> buildDingTalk(asset);
            case WECOM -> throw new IllegalArgumentException("Channel type not yet wired: " + asset.getType());
        };
    }

    private GatewayBootstrap buildFeishu(ChannelAsset asset) {
        ReleasedChannelAgent released = releasedAgents.resolve(asset);
        HarnessAgent agent = agentFactory.build(released.config(), null);
        String agentKey = released.agentKey();

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
        Channel channel = new FeishuWsChannel(
                asset.getChannelKey(),
                routing,
                appId,
                appSecret,
                larkClient,
                dialogue,
                identityResolver,
                released.agentId(),
                released.agentName(),
                asset.getType().name());

        log.info(
                "Building Feishu long-connection channel '{}' bound to agent '{}' (dmScope={})",
                asset.getChannelKey(),
                agentKey,
                asset.getDmScope());

        return GatewayBootstrap.builder()
                .agent(agentKey, agent)
                .channel(channel)
                .build();
    }

    private GatewayBootstrap buildWechatIlink(ChannelAsset asset) {
        ReleasedChannelAgent released = releasedAgents.resolve(asset);
        HarnessAgent agent = agentFactory.build(released.config(), null);
        String agentKey = released.agentKey();

        ChannelIlinkSession session =
                ilinkSessions.findByChannelId(asset.getId()).orElse(null);
        if (session == null
                || session.getLoginStatus() != IlinkLoginStatus.LOGGED_IN
                || session.getBotTokenCiphertext() == null
                || session.getBotTokenCiphertext().isBlank()) {
            throw new IllegalStateException(
                    "WeChat iLink channel '" + asset.getChannelKey() + "' is not logged in; scan the QR code first");
        }
        String botToken = cipher.decrypt(session.getBotTokenCiphertext());
        Map<String, Object> props = readMap(asset.getConfigJson());
        String apiBase = asString(props, "apiBase");
        String channelVersion = asString(props, "channelVersion");
        IlinkClient client = new IlinkClient(apiBase, channelVersion);

        ChannelConfig routing = ChannelConfig.builder(asset.getChannelKey())
                .defaultAgentId(agentKey)
                .build();
        WeChatIlinkChannel channel = new WeChatIlinkChannel(
                asset.getId(),
                asset.getChannelKey(),
                routing,
                client,
                botToken,
                ilinkSessions,
                tx,
                dialogue,
                identityResolver,
                released.agentId(),
                released.agentName());

        log.info(
                "Building WeChat iLink channel '{}' bound to agent '{}' (botId={})",
                asset.getChannelKey(),
                agentKey,
                session.getBotId());

        return GatewayBootstrap.builder()
                .agent(agentKey, agent)
                .channel(channel)
                .build();
    }

    private GatewayBootstrap buildDingTalk(ChannelAsset asset) {
        ReleasedChannelAgent released = releasedAgents.resolve(asset);
        HarnessAgent agent = agentFactory.build(released.config(), null);
        String agentKey = released.agentKey();

        Map<String, Object> props = new LinkedHashMap<>(readMap(asset.getConfigJson()));
        Map<String, String> secrets = readSecrets(asset.getSecretsCiphertext());
        copyIfPresent(props, secrets, "appSecret");
        DingTalkChannelProperties properties = DingTalkChannelProperties.from(asset.getChannelKey(), props);

        ChannelConfig routing = ChannelConfig.builder(asset.getChannelKey())
                .defaultAgentId(agentKey)
                .dmScope(toDmScope(asset.getDmScope()))
                .build();
        Channel channel = new DingTalkStreamChannel(
                asset.getChannelKey(),
                routing,
                properties,
                dialogue,
                identityResolver,
                released.agentId(),
                released.agentName(),
                asset.getType().name());

        log.info(
                "Building DingTalk stream channel '{}' bound to agent '{}' (appKey={}, robotCode={}, dmScope={})",
                asset.getChannelKey(),
                agentKey,
                properties.appKey(),
                properties.robotCode(),
                asset.getDmScope());

        return GatewayBootstrap.builder()
                .agent(agentKey, agent)
                .channel(channel)
                .build();
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
