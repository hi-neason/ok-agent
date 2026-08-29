package io.okagent.module.release.application;

import io.okagent.module.channel.domain.ChannelAsset;

public interface ReleasedChannelAgentResolver {

    /** Resolves and validates the immutable Agent version currently promoted on a channel. */
    ReleasedChannelAgent resolve(ChannelAsset channel);
}
