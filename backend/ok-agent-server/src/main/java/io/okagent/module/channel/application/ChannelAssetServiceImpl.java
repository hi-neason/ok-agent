package io.okagent.module.channel.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.module.channel.domain.ChannelAsset;
import io.okagent.module.channel.domain.ChannelDmScope;
import io.okagent.module.channel.domain.ChannelIlinkSession;
import io.okagent.module.channel.domain.ChannelRuntimeStatus;
import io.okagent.module.channel.domain.ChannelType;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.channel.infrastructure.persistence.ChannelAssetRepository;
import io.okagent.module.channel.infrastructure.persistence.ChannelIlinkSessionRepository;
import io.okagent.module.channel.application.runtime.ChannelRuntimeEvent;
import io.okagent.module.channel.application.runtime.dingtalk.DingTalkRegistrationService;
import io.okagent.module.channel.application.runtime.wechat.WechatLoginRegistrationService;
import io.okagent.module.model.application.ApiKeyCipher;
import io.okagent.module.channel.application.ChannelAssetRequest;
import io.okagent.module.channel.application.ChannelAssetResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChannelAssetServiceImpl implements ChannelAssetService {

    private final ChannelAssetRepository repository;
    private final AgentAssetRepository agentRepository;
    private final ChannelIlinkSessionRepository ilinkSessions;
    private final ApiKeyCipher cipher;
    private final ApplicationEventPublisher events;
    private final ChannelUserService channelUsers;
    private final ChannelOperatorService channelOperators;
    private final WechatLoginRegistrationService wechatRegistration;
    private final DingTalkRegistrationService dingtalkRegistration;
    private final ObjectMapper objectMapper;
    private final String publicBaseUrl;

    public ChannelAssetServiceImpl(
            ChannelAssetRepository repository,
            AgentAssetRepository agentRepository,
            ChannelIlinkSessionRepository ilinkSessions,
            ApiKeyCipher cipher,
            ApplicationEventPublisher events,
            ChannelUserService channelUsers,
            ChannelOperatorService channelOperators,
            WechatLoginRegistrationService wechatRegistration,
            DingTalkRegistrationService dingtalkRegistration,
            ObjectMapper objectMapper,
            @Value("${ok-agent.channels.public-base-url:}") String publicBaseUrl) {
        this.repository = repository;
        this.agentRepository = agentRepository;
        this.ilinkSessions = ilinkSessions;
        this.cipher = cipher;
        this.events = events;
        this.channelUsers = channelUsers;
        this.channelOperators = channelOperators;
        this.wechatRegistration = wechatRegistration;
        this.dingtalkRegistration = dingtalkRegistration;
        this.objectMapper = objectMapper;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChannelAssetResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return repository.findAll(pageable)
                .map(a -> ChannelAssetResponse.from(
                        a,
                        publicBaseUrl,
                        channelUsers.countByChannel(a.getChannelKey()),
                        channelOperators.assignedOperatorNames(a.getId())));
    }

    @Override
    @Transactional
    public ChannelAssetResponse create(ChannelAssetRequest request) {
        // DingTalk scan-to-bind: claim the AppKey/AppSecret from the independent registration
        // session and fold them into the request before validation/config serialization, so the
        // normal write path persists appKey/robotCode (=AppKey) and the encrypted appSecret.
        if (request.type() == ChannelType.DINGTALK && request.dingtalkLoginId() != null
                && !request.dingtalkLoginId().isBlank()) {
            DingTalkRegistrationService.ClaimedCredentials creds =
                    dingtalkRegistration.consume(request.dingtalkLoginId());
            if (creds == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先使用钉钉扫码确认后再保存");
            }
            ChannelAssetRequest.DingTalkConfig fromForm = request.dingtalk();
            ChannelAssetRequest.DingTalkConfig filled = new ChannelAssetRequest.DingTalkConfig(
                    creds.appKey(),
                    creds.appSecret(),
                    creds.appKey(), // robotCode == AppKey for a device-auth-created robot
                    fromForm != null ? fromForm.apiBase() : null,
                    fromForm != null ? fromForm.oapiBase() : null,
                    fromForm != null ? fromForm.streamRegisterUrl() : null);
            request = new ChannelAssetRequest(
                    request.name(),
                    request.type(),
                    request.boundAgentId(),
                    request.dmScope(),
                    request.feishu(),
                    request.wechat(),
                    filled,
                    request.wechatLoginId(),
                    request.dingtalkLoginId(),
                    request.enabled());
        }
        validate(request);
        String channelKey = UUID.randomUUID().toString();
        String configJson = writeConfig(channelKey, request);
        String secretCiphertext = cipher.encrypt(writeSecrets(request, Map.of()));
        String secretFlags = write(mergeSecretFlags(null, request));
        ChannelAsset asset = new ChannelAsset(
                UUID.randomUUID(),
                channelKey,
                request.name(),
                request.type() == null ? ChannelType.FEISHU : request.type(),
                request.boundAgentId(),
                request.dmScope() == null ? ChannelDmScope.PER_PEER : request.dmScope(),
                configJson,
                secretCiphertext,
                secretFlags,
                request.enabled(),
                "system");
        ChannelAsset saved = repository.save(asset);
        if (saved.getType() == ChannelType.WECHAT) {
            // New "scan-first" flow: the QR was driven by an independent registration session.
            // Claim its confirmed credentials here and persist them onto a fresh iLink session in
            // the same transaction. No channel row existed before save, so canceling the dialog
            // leaves nothing behind — and there is no concurrent-insert/delete race.
            WechatLoginRegistrationService.ClaimedCredentials creds =
                    wechatRegistration.consume(request.wechatLoginId());
            if (creds == null) {
                // Roll back the channel insert: a WECHAT channel cannot be used without a scan.
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先使用微信扫码确认后再保存");
            }
            ChannelIlinkSession session = new ChannelIlinkSession(saved.getId());
            session.markLoggedIn(creds.botTokenCiphertext(), creds.botId(), creds.ilinkUserId());
            ilinkSessions.save(session);
        }
        reconcileAfterCommit(saved.getId());
        return ChannelAssetResponse.from(saved, publicBaseUrl, 0);
    }

    @Override
    @Transactional
    public ChannelAssetResponse update(UUID id, ChannelAssetRequest request) {
        validate(request);
        ChannelAsset asset = find(id);
        Map<String, String> existingSecrets = readSecrets(asset.getSecretsCiphertext());
        String mergedSecrets = writeSecrets(request, existingSecrets);
        Map<String, Boolean> configuredFlags = mergeSecretFlags(asset.getSecretsConfiguredJson(), request);
        asset.update(
                request.name(),
                request.boundAgentId(),
                request.dmScope() == null ? ChannelDmScope.PER_PEER : request.dmScope(),
                writeConfig(asset.getChannelKey(), request),
                cipher.encrypt(mergedSecrets),
                write(configuredFlags),
                request.enabled(),
                "system");
        ChannelAsset saved = repository.save(asset);
        reconcileAfterCommit(saved.getId());
        return ChannelAssetResponse.from(saved, publicBaseUrl, channelUsers.countByChannel(saved.getChannelKey()));
    }

    @Override
    @Transactional
    public ChannelAssetResponse setEnabled(UUID id, boolean enabled) {
        ChannelAsset asset = find(id);
        asset.setEnabled(enabled);
        if (!enabled) {
            asset.reportRuntime(ChannelRuntimeStatus.STOPPED, null);
        }
        ChannelAsset saved = repository.save(asset);
        reconcileAfterCommit(saved.getId());
        return ChannelAssetResponse.from(saved, publicBaseUrl, channelUsers.countByChannel(saved.getChannelKey()));
    }

    @Override
    @Transactional
    public ChannelAssetResponse start(UUID id) {
        ChannelAsset asset = find(id);
        asset.reportRuntime(ChannelRuntimeStatus.STARTING, null);
        ChannelAsset saved = repository.save(asset);
        reconcileAfterCommit(saved.getId());
        return ChannelAssetResponse.from(saved, publicBaseUrl, channelUsers.countByChannel(saved.getChannelKey()));
    }

    @Override
    @Transactional
    public ChannelAssetResponse stop(UUID id) {
        ChannelAsset asset = find(id);
        asset.reportRuntime(ChannelRuntimeStatus.STOPPED, null);
        ChannelAsset saved = repository.save(asset);
        reconcileAfterCommit(saved.getId());
        return ChannelAssetResponse.from(saved, publicBaseUrl, channelUsers.countByChannel(saved.getChannelKey()));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        // Version-free bulk delete: a duplicate/concurrent delete (e.g. React StrictMode firing
        // the request twice) simply removes 0 rows and returns 204, instead of loading the entity
        // and hitting StaleStateException on the @Version-gated DELETE. The DB ON DELETE CASCADE
        // takes care of the child channel_ilink_session row.
        repository.deleteChannelById(id);
        stopAfterCommit(id);
    }

    private void validate(ChannelAssetRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Channel request is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Channel name is required");
        }
        if (request.type() != ChannelType.FEISHU
                && request.type() != ChannelType.WECHAT
                && request.type() != ChannelType.DINGTALK) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported channel type: " + request.type());
        }
        if (request.boundAgentId() != null && !agentRepository.existsById(request.boundAgentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bound agent does not exist");
        }
        if (request.type() == ChannelType.FEISHU
                && (request.feishu() == null
                        || request.feishu().appId() == null
                        || request.feishu().appId().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "feishu.appId is required");
        }
        if (request.type() == ChannelType.DINGTALK
                && (request.dingtalk() == null
                        || request.dingtalk().appKey() == null
                        || request.dingtalk().appKey().isBlank()
                        || request.dingtalk().robotCode() == null
                        || request.dingtalk().robotCode().isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "dingtalk.appKey and dingtalk.robotCode are required");
        }
    }

    private ChannelAsset find(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel asset not found"));
    }

    private String writeConfig(String channelKey, ChannelAssetRequest request) {
        Map<String, Object> config = new TreeMap<>();
        if (request.type() == ChannelType.WECHAT) {
            if (request.wechat() != null) {
                if (request.wechat().apiBase() != null && !request.wechat().apiBase().isBlank()) {
                    config.put("apiBase", request.wechat().apiBase());
                }
                if (request.wechat().channelVersion() != null
                        && !request.wechat().channelVersion().isBlank()) {
                    config.put("channelVersion", request.wechat().channelVersion());
                }
            }
            return write(config);
        }
        if (request.type() == ChannelType.DINGTALK) {
            ChannelAssetRequest.DingTalkConfig d = request.dingtalk();
            config.put("appKey", d.appKey());
            config.put("robotCode", d.robotCode());
            putConfigIfNotBlank(config, "apiBase", d.apiBase());
            putConfigIfNotBlank(config, "oapiBase", d.oapiBase());
            putConfigIfNotBlank(config, "streamRegisterUrl", d.streamRegisterUrl());
            return write(config);
        }
        config.put("appId", request.feishu().appId());
        if (request.feishu().apiBase() != null && !request.feishu().apiBase().isBlank()) {
            config.put("apiBase", request.feishu().apiBase());
        }
        String callbackPath = request.feishu().callbackPath();
        if (callbackPath == null || callbackPath.isBlank()) {
            callbackPath = "/api/channels/feishu/" + channelKey + "/callback";
        }
        config.put("callbackPath", callbackPath);
        return write(config);
    }

    private String writeSecrets(ChannelAssetRequest request, Map<String, String> existing) {
        Map<String, String> secrets = new LinkedHashMap<>(existing);
        if (request.type() == ChannelType.WECHAT) {
            // iLink authenticates via the QR-scanned bot_token stored on channel_ilink_session;
            // there are no static secrets to keep here.
            return write(secrets);
        }
        if (request.type() == ChannelType.DINGTALK && request.dingtalk() != null) {
            putIfNotBlank(secrets, "appSecret", request.dingtalk().appSecret());
            return write(secrets);
        }
        if (request.feishu() != null) {
            putIfNotBlank(secrets, "appSecret", request.feishu().appSecret());
            putIfNotBlank(secrets, "encryptKey", request.feishu().encryptKey());
            putIfNotBlank(secrets, "verificationToken", request.feishu().verificationToken());
        }
        return write(secrets);
    }

    private Map<String, Boolean> mergeSecretFlags(String existingFlagsJson, ChannelAssetRequest request) {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        flags.putAll(readSecretFlags(existingFlagsJson));
        if (request.type() == ChannelType.FEISHU) {
            flags.putAll(ChannelAssetResponse.configuredSecrets(request.feishu()));
        } else if (request.type() == ChannelType.DINGTALK && request.dingtalk() != null) {
            if (notBlank(request.dingtalk().appSecret())) {
                flags.put("appSecret", true);
            }
        }
        return flags;
    }

    /** Reads previously persisted secret flags, tolerating the legacy JSON-array format. */
    private Map<String, Boolean> readSecretFlags(String json) {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return flags;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isObject()) {
                node.fields()
                        .forEachRemaining(
                                e -> flags.put(e.getKey(), e.getValue().asBoolean()));
            } else if (node.isArray()) {
                node.forEach(e -> flags.put(e.asText(), true));
            }
        } catch (Exception e) {
            // ignore malformed flags
        }
        return flags;
    }

    private Map<String, String> readSecrets(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            String json = cipher.decrypt(ciphertext);
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private void putIfNotBlank(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private void putConfigIfNotBlank(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize channel config", e);
        }
    }

    private void reconcileAfterCommit(UUID channelId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    events.publishEvent(new ChannelRuntimeEvent(channelId, false));
                }
            });
        } else {
            events.publishEvent(new ChannelRuntimeEvent(channelId, false));
        }
    }

    private void stopAfterCommit(UUID channelId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    events.publishEvent(new ChannelRuntimeEvent(channelId, true));
                }
            });
        } else {
            events.publishEvent(new ChannelRuntimeEvent(channelId, true));
        }
    }
}
