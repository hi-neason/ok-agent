package io.okagent.module.channel.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Auto-discovered identity of a person who talks to a channel-bound bot. One row per {@code
 * (channelType, channelKey, externalId)}: the same real person talking to two different bots (or on
 * two different providers) yields two rows. The {@code externalId} is the provider-level sender id
 * used as the runtime {@code userId} (e.g. Feishu open_id); {@code unionId} / {@code tenantKey} are
 * retained for future cross-app / cross-tenant aggregation.
 *
 * <p>{@code linkedUserId} optionally points to a system-managed {@code app_user}, reserved for later
 * "claim / bind a channel person into a system account" workflows. It is not populated by the MVP.
 */
@Entity
@Table(
        name = "channel_user_identity",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_channel_external",
                        columnNames = {"channel_type", "channel_key", "external_id"}))
public class ChannelUserIdentity {

    @Id
    private UUID id;

    @Column(name = "channel_type", nullable = false, length = 32)
    private String channelType;

    @Column(name = "channel_key", nullable = false, length = 64)
    private String channelKey;

    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;

    @Column(name = "union_id", length = 128)
    private String unionId;

    @Column(name = "tenant_key", length = 128)
    private String tenantKey;

    @Column(name = "display_name", length = 256)
    private String displayName;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "linked_user_id")
    private UUID linkedUserId;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    @Column(name = "message_count", nullable = false)
    private long messageCount;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    protected ChannelUserIdentity() {}

    /** Rehydration constructor used by persistence layer to populate all fields. */
    public ChannelUserIdentity(
            String channelType,
            String channelKey,
            String externalId,
            String unionId,
            String tenantKey,
            String displayName,
            String avatarUrl,
            Instant firstSeenAt,
            Instant lastSeenAt,
            Instant lastMessageAt,
            long messageCount,
            Instant createdAt,
            Instant updatedAt) {
        this(
                null,
                null,
                channelType,
                channelKey,
                externalId,
                unionId,
                tenantKey,
                displayName,
                avatarUrl,
                firstSeenAt,
                lastSeenAt,
                lastMessageAt,
                messageCount,
                createdAt,
                updatedAt);
    }

    /** Full rehydration constructor including the surrogate id and linked one-user-id. */
    public ChannelUserIdentity(
            UUID id,
            UUID linkedUserId,
            String channelType,
            String channelKey,
            String externalId,
            String unionId,
            String tenantKey,
            String displayName,
            String avatarUrl,
            Instant firstSeenAt,
            Instant lastSeenAt,
            Instant lastMessageAt,
            long messageCount,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.linkedUserId = linkedUserId;
        this.channelType = channelType;
        this.channelKey = channelKey;
        this.externalId = externalId;
        this.unionId = blankToNull(unionId);
        this.tenantKey = blankToNull(tenantKey);
        this.displayName = blankToNull(displayName);
        this.avatarUrl = blankToNull(avatarUrl);
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.lastMessageAt = lastMessageAt;
        this.messageCount = messageCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Records a new inbound message, bumping counters and refreshing optional profile fields. */
    public void touch(String displayName, String avatarUrl, String unionId, String tenantKey) {
        Instant now = Instant.now();
        this.lastSeenAt = now;
        this.lastMessageAt = now;
        this.messageCount++;
        if (displayName != null && !displayName.isBlank()) {
            this.displayName = displayName;
        }
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            this.avatarUrl = avatarUrl;
        }
        if (unionId != null && !unionId.isBlank()) {
            this.unionId = unionId;
        }
        if (tenantKey != null && !tenantKey.isBlank()) {
            this.tenantKey = tenantKey;
        }
        this.updatedAt = now;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public UUID getId() {
        return id;
    }

    public String getChannelType() {
        return channelType;
    }

    public String getChannelKey() {
        return channelKey;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getUnionId() {
        return unionId;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public UUID getLinkedUserId() {
        return linkedUserId;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public long getMessageCount() {
        return messageCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
