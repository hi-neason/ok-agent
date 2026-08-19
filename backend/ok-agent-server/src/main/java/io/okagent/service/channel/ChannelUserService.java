package io.okagent.service.channel;

import io.okagent.domain.channel.ChannelUserIdentity;
import java.util.List;

/**
 * Tracks people who converse with channel-bound bots. Identity records are created/updated
 * automatically when an inbound message arrives; the management UI only reads them.
 */
public interface ChannelUserService {

    /**
     * Records an inbound sender, creating the identity on first sight and refreshing last-seen
     * timestamps/counters afterwards. MUST be non-throwing and non-blocking from the caller's
     * perspective.
     */
    void recordInbound(
            String channelType,
            String channelKey,
            String externalId,
            String unionId,
            String tenantKey,
            String displayName,
            String avatarUrl);

    /** Lists channel users, most recently seen first, with optional channel filters. */
    List<ChannelUserIdentity> list(String channelType, String channelKey, int limit);

    /** Counts distinct identities for a channel instance. */
    long countByChannel(String channelKey);
}
