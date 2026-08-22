package io.okagent.domain.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-channel WeChat iLink (ClawBot) login session. Holds the QR-login flow state, the encrypted
 * bot_token, and the long-polling cursor. There is exactly one row per {@code WECHAT} channel.
 *
 * <p>These fields are separated from {@link ChannelAsset} because they mutate frequently (QR
 * polling, cursor advancement) and would otherwise contend with the channel's optimistic lock.
 * Writes go through targeted repository updates rather than full-entity merges.
 */
@Entity
@Table(name = "channel_ilink_session")
public class ChannelIlinkSession {

    @Id
    @Column(name = "channel_id")
    private UUID channelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "login_status", nullable = false, length = 16)
    private IlinkLoginStatus loginStatus;

    /** Encrypted iLink bot_token (Bearer credential), or null when not logged in. */
    @Column(name = "bot_token_ciphertext", columnDefinition = "TEXT")
    private String botTokenCiphertext;

    @Column(name = "bot_id", length = 128)
    private String botId;

    @Column(name = "ilink_user_id", length = 128)
    private String ilinkUserId;

    @Column(name = "qrcode_token", length = 255)
    private String qrcodeToken;

    @Column(name = "qrcode_url", length = 512)
    private String qrcodeUrl;

    /** Opaque long-polling cursor; empty string on first poll. */
    @Column(name = "poll_cursor", nullable = false, length = 512)
    private String pollCursor;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "logged_in_at")
    private Instant loggedInAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChannelIlinkSession() {}

    public ChannelIlinkSession(UUID channelId) {
        this.channelId = channelId;
        this.loginStatus = IlinkLoginStatus.LOGGED_OUT;
        this.pollCursor = "";
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markQrIssued(String qrcodeToken, String qrcodeUrl) {
        this.qrcodeToken = qrcodeToken;
        this.qrcodeUrl = qrcodeUrl;
        this.loginStatus = IlinkLoginStatus.WAITING_QR;
        this.lastError = null;
        this.updatedAt = Instant.now();
    }

    public void markScanned() {
        if (this.loginStatus == IlinkLoginStatus.WAITING_QR) {
            this.loginStatus = IlinkLoginStatus.SCANNED;
            this.updatedAt = Instant.now();
        }
    }

    public void markLoggedIn(String botTokenCiphertext, String botId, String ilinkUserId) {
        this.botTokenCiphertext = botTokenCiphertext;
        this.botId = botId;
        this.ilinkUserId = ilinkUserId;
        this.loginStatus = IlinkLoginStatus.LOGGED_IN;
        this.qrcodeToken = null;
        this.qrcodeUrl = null;
        this.lastError = null;
        this.loggedInAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markExpired() {
        this.loginStatus = IlinkLoginStatus.EXPIRED;
        this.qrcodeToken = null;
        this.qrcodeUrl = null;
        this.updatedAt = Instant.now();
    }

    public void markError(String error) {
        this.loginStatus = IlinkLoginStatus.ERROR;
        this.lastError = error;
        this.updatedAt = Instant.now();
    }

    public void clearLogin() {
        this.botTokenCiphertext = null;
        this.botId = null;
        this.ilinkUserId = null;
        this.pollCursor = "";
        this.qrcodeToken = null;
        this.qrcodeUrl = null;
        this.loginStatus = IlinkLoginStatus.LOGGED_OUT;
        this.lastError = null;
        this.loggedInAt = null;
        this.updatedAt = Instant.now();
    }

    public UUID getChannelId() {
        return channelId;
    }

    public IlinkLoginStatus getLoginStatus() {
        return loginStatus;
    }

    public String getBotTokenCiphertext() {
        return botTokenCiphertext;
    }

    public String getBotId() {
        return botId;
    }

    public String getIlinkUserId() {
        return ilinkUserId;
    }

    public String getQrcodeToken() {
        return qrcodeToken;
    }

    public String getQrcodeUrl() {
        return qrcodeUrl;
    }

    public String getPollCursor() {
        return pollCursor;
    }

    public void setPollCursor(String pollCursor) {
        this.pollCursor = pollCursor == null ? "" : pollCursor;
        this.updatedAt = Instant.now();
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getLoggedInAt() {
        return loggedInAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
