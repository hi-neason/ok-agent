package io.okagent.module.identity.api;

import io.okagent.domain.channel.ChannelUserIdentity;
import java.time.Instant;

/** One provider-side identity (e.g. a Feishu open_id) bound to a one-user-id. */
public record ChannelIdentityView(
        String channelType,
        String channelKey,
        String externalId,
        String unionId,
        String tenantKey,
        String displayName,
        String avatarUrl,
        long messageCount,
        Instant firstSeenAt,
        Instant lastSeenAt) {

    public static ChannelIdentityView from(ChannelUserIdentity i) {
        return new ChannelIdentityView(
                i.getChannelType(),
                i.getChannelKey(),
                i.getExternalId(),
                i.getUnionId(),
                i.getTenantKey(),
                i.getDisplayName(),
                i.getAvatarUrl(),
                i.getMessageCount(),
                i.getFirstSeenAt(),
                i.getLastSeenAt());
    }
}
