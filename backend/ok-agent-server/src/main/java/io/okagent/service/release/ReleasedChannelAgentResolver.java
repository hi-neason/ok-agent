package io.okagent.service.release;

import io.okagent.domain.channel.ChannelAsset;

public interface ReleasedChannelAgentResolver {

    /** Resolves and validates the immutable Agent version currently promoted on a channel. */
    ReleasedChannelAgent resolve(ChannelAsset channel);
}
