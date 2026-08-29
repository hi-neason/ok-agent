package io.okagent.module.channel.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.module.channel.domain.ChannelAsset;
import io.okagent.module.channel.domain.ChannelIlinkSession;
import io.okagent.module.channel.domain.ChannelRuntimeStatus;
import io.okagent.module.channel.domain.ChannelType;
import io.okagent.module.channel.domain.IlinkLoginStatus;
import io.okagent.module.channel.infrastructure.persistence.ChannelAssetRepository;
import io.okagent.module.channel.infrastructure.persistence.ChannelIlinkSessionRepository;
import io.okagent.module.channel.application.runtime.ChannelRuntimeEvent;
import io.okagent.module.channel.application.runtime.wechat.IlinkClient;
import io.okagent.module.channel.application.runtime.wechat.IlinkException;
import io.okagent.module.model.application.ApiKeyCipher;
import io.okagent.module.channel.application.WechatIlinkStatusResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WechatIlinkLoginServiceImpl implements WechatIlinkLoginService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ChannelAssetRepository channels;
    private final ChannelIlinkSessionRepository sessions;
    private final ApiKeyCipher cipher;
    private final ApplicationEventPublisher events;

    public WechatIlinkLoginServiceImpl(
            ChannelAssetRepository channels,
            ChannelIlinkSessionRepository sessions,
            ApiKeyCipher cipher,
            ApplicationEventPublisher events) {
        this.channels = channels;
        this.sessions = sessions;
        this.cipher = cipher;
        this.events = events;
    }

    @Override
    @Transactional
    public WechatIlinkStatusResponse startLogin(UUID channelId) {
        ChannelAsset channel = requireWechatChannel(channelId);
        // The session row is created eagerly when the WECHAT channel is created, so on the hot
        // path take an exclusive row lock up front. This serializes concurrent startLogin() calls
        // (e.g. the QR panel auto-start firing twice in React StrictMode) without ever upgrading
        // a shared lock to an exclusive one — the pattern that previously caused a deadlock.
        ChannelIlinkSession session;
        try {
            session = sessions.findByChannelIdForUpdate(channelId).orElse(null);
            if (session == null) {
                // Legacy channel created before eager provisioning: ensure a row exists, then re-lock.
                sessions.insertIfAbsent(channelId);
                session = sessions.findByChannelIdForUpdate(channelId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "渠道登录会话不存在"));
            }
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // The channel was deleted (by a concurrent close/cancel) between the existence check
            // and the session insert — its FK is gone. Report a clean 404 instead of a 500.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "渠道已被删除", e);
        }
        try {
            IlinkClient client = buildClient(channel);
            IlinkClient.QrSession qr = client.requestQrCode();
            session.markQrIssued(qr.qrcodeToken(), qr.qrcodeImgContent());
            sessions.save(session);
            return WechatIlinkStatusResponse.from(session);
        } catch (Exception e) {
            session.markError(wrap(e));
            sessions.save(session);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "iLink 登录二维码获取失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional
    public WechatIlinkStatusResponse pollStatus(UUID channelId) {
        ChannelAsset channel = requireWechatChannel(channelId);
        // Lock the row up front: pollStatus runs on a tight timer and may fire concurrently
        // (StrictMode / retries). A plain find + later update can deadlock the same way startLogin
        // did, so serialize through the exclusive lock.
        ChannelIlinkSession session = sessions.findByChannelIdForUpdate(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "请先获取登录二维码"));
        if (session.getLoginStatus() == IlinkLoginStatus.LOGGED_IN) {
            return WechatIlinkStatusResponse.from(session);
        }
        String qrcodeToken = session.getQrcodeToken();
        if (qrcodeToken == null || qrcodeToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "没有待确认的二维码，请重新获取");
        }
        try {
            IlinkClient client = buildClient(channel);
            IlinkClient.QrStatus status = client.pollQrStatus(qrcodeToken);
            if (status.expired()) {
                session.markExpired();
            } else if (status.confirmed() && status.botToken() != null) {
                session.markLoggedIn(
                        cipher.encrypt(status.botToken()), status.botId(), status.ilinkUserId());
                sessions.save(session);
                reconcileAfterCommit(channelId);
                return WechatIlinkStatusResponse.from(session);
            } else if ("scaned".equalsIgnoreCase(status.status())) {
                session.markScanned();
            }
            sessions.save(session);
            return WechatIlinkStatusResponse.from(session);
        } catch (Exception e) {
            session.markError(wrap(e));
            sessions.save(session);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "iLink 登录状态查询失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public WechatIlinkStatusResponse getStatus(UUID channelId) {
        requireWechatChannel(channelId);
        return sessions.findByChannelId(channelId)
                .map(WechatIlinkStatusResponse::from)
                .orElseGet(() -> new WechatIlinkStatusResponse(
                        channelId, IlinkLoginStatus.LOGGED_OUT, null, null, null, null, null, null, null));
    }

    @Override
    @Transactional
    public WechatIlinkStatusResponse logout(UUID channelId) {
        ChannelAsset channel = requireWechatChannel(channelId);
        ChannelIlinkSession session = sessions.findByChannelId(channelId).orElse(null);
        if (session != null) {
            session.clearLogin();
            sessions.save(session);
        }
        channel.reportRuntime(ChannelRuntimeStatus.STOPPED, null);
        channels.save(channel);
        reconcileAfterCommit(channelId);
        return WechatIlinkStatusResponse.from(session);
    }

    private ChannelAsset requireWechatChannel(UUID channelId) {
        ChannelAsset channel = channels.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
        if (channel.getType() != ChannelType.WECHAT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该渠道不是微信 iLink 类型");
        }
        return channel;
    }

    private IlinkClient buildClient(ChannelAsset channel) {
        String apiBase = readConfig(channel.getConfigJson()).getOrDefault("apiBase", "https://ilinkai.weixin.qq.com");
        String channelVersion = readConfig(channel.getConfigJson()).getOrDefault("channelVersion", "0.1.0");
        return new IlinkClient(apiBase, channelVersion);
    }

    private Map<String, String> readConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(configJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String wrap(Exception e) {
        if (e instanceof IlinkException il) {
            return "iLink 错误(" + il.statusCode() + "): " + il.getMessage();
        }
        return e.getMessage();
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
}
