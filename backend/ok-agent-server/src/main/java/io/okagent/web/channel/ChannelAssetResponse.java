package io.okagent.web.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.domain.channel.ChannelAsset;
import io.okagent.domain.channel.ChannelDmScope;
import io.okagent.domain.channel.ChannelRuntimeStatus;
import io.okagent.domain.channel.ChannelType;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * Read view of a channel. Secret values are never returned; only a boolean per secret indicates
 * whether a value is currently stored.
 */
public record ChannelAssetResponse(
        UUID id,
        String channelKey,
        String name,
        ChannelType type,
        UUID boundAgentId,
        ChannelDmScope dmScope,
        FeishuView feishu,
        WechatView wechat,
        DingTalkView dingtalk,
        boolean enabled,
        ChannelRuntimeStatus runtimeStatus,
        String lastError,
        String callbackUrl,
        long userCount,
        List<String> operatorNames,
        Instant createdAt,
        Instant updatedAt) {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Feishu non-secret settings plus which secrets are configured. */
    public record FeishuView(
            String appId,
            String apiBase,
            String callbackPath,
            boolean appSecretConfigured,
            boolean encryptKeyConfigured,
            boolean verificationTokenConfigured) {}

    /** WeChat iLink (ClawBot) settings. Login state is fetched via the dedicated session endpoint. */
    public record WechatView(String apiBase, String channelVersion) {}

    /** DingTalk Stream settings; appSecret is write-only, only a configured flag is exposed. */
    public record DingTalkView(
            String appKey,
            String robotCode,
            String apiBase,
            String oapiBase,
            String streamRegisterUrl,
            boolean appSecretConfigured) {}

    public static ChannelAssetResponse from(ChannelAsset asset, String publicBaseUrl, long userCount) {
        return from(asset, publicBaseUrl, userCount, List.of());
    }

    public static ChannelAssetResponse from(
            ChannelAsset asset, String publicBaseUrl, long userCount, List<String> operatorNames) {
        Map<String, Object> config = readMap(asset.getConfigJson());
        Map<String, Object> secretFlags = readMap(asset.getSecretsConfiguredJson());

        FeishuView feishu = null;
        WechatView wechat = null;
        DingTalkView dingtalk = null;
        if (asset.getType() == ChannelType.FEISHU) {
            String configuredCallbackPath =
                    str(config, "callbackPath", "/api/channels/feishu/" + asset.getChannelKey() + "/callback");
            feishu = new FeishuView(
                    str(config, "appId", null),
                    str(config, "apiBase", "https://open.feishu.cn"),
                    configuredCallbackPath,
                    bool(secretFlags, "appSecret"),
                    bool(secretFlags, "encryptKey"),
                    bool(secretFlags, "verificationToken"));
        } else if (asset.getType() == ChannelType.WECHAT) {
            wechat = new WechatView(
                    str(config, "apiBase", "https://ilinkai.weixin.qq.com"),
                    str(config, "channelVersion", "1.0.2"));
        } else if (asset.getType() == ChannelType.DINGTALK) {
            dingtalk = new DingTalkView(
                    str(config, "appKey", null),
                    str(config, "robotCode", null),
                    str(config, "apiBase", "https://api.dingtalk.com"),
                    str(config, "oapiBase", "https://oapi.dingtalk.com"),
                    str(
                            config,
                            "streamRegisterUrl",
                            "https://api.dingtalk.com/v1.0/gateway/connections/open"),
                    bool(secretFlags, "appSecret"));
        }

        String callbackUrl = null;
        if (publicBaseUrl != null && !publicBaseUrl.isBlank() && feishu != null) {
            String base = publicBaseUrl.endsWith("/")
                    ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                    : publicBaseUrl;
            callbackUrl = base + feishu.callbackPath();
        }

        return new ChannelAssetResponse(
                asset.getId(),
                asset.getChannelKey(),
                asset.getName(),
                asset.getType(),
                asset.getBoundAgentId(),
                asset.getDmScope(),
                feishu,
                wechat,
                dingtalk,
                asset.isEnabled(),
                asset.getRuntimeStatus(),
                asset.getLastError(),
                callbackUrl,
                userCount,
                List.copyOf(operatorNames),
                asset.getCreatedAt(),
                asset.getUpdatedAt());
    }

    private static Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String str(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        return v == null ? fallback : v.toString();
    }

    private static boolean bool(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
    }

    /** Returns a map of secret field name → configured flag for secrets present in the request. */
    public static Map<String, Boolean> configuredSecrets(ChannelAssetRequest.FeishuConfig feishu) {
        Map<String, Boolean> flags = new java.util.LinkedHashMap<>();
        if (feishu == null) {
            return flags;
        }
        if (notBlank(feishu.appSecret())) flags.put("appSecret", true);
        if (notBlank(feishu.encryptKey())) flags.put("encryptKey", true);
        if (notBlank(feishu.verificationToken())) flags.put("verificationToken", true);
        return flags;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
