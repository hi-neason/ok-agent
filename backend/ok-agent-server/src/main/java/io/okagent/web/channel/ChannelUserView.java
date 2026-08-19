package io.okagent.web.channel;

import io.okagent.domain.channel.ChannelUserIdentity;
import java.time.Instant;

/**
 * Read-only view of a person who has talked to a channel-bound bot. Identity is scoped to one
 * (channel type, channel instance, external id); cross-app aggregation is reserved for future work.
 */
public record ChannelUserView(
        String channelType,
        String channelKey,
        String externalId,
        String displayName,
        String tenantKey,
        Instant firstSeenAt,
        Instant lastSeenAt,
        long messageCount) {

    public static ChannelUserView from(ChannelUserIdentity identity) {
        return new ChannelUserView(
                identity.getChannelType(),
                identity.getChannelKey(),
                identity.getExternalId(),
                identity.getDisplayName(),
                identity.getTenantKey(),
                identity.getFirstSeenAt(),
                identity.getLastSeenAt(),
                identity.getMessageCount());
    }
}
