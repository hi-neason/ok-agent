package io.okagent.module.channel.application.runtime;

import java.util.UUID;

/**
 * Internal event fired (after the originating transaction commits) whenever a channel's persisted
 * configuration changes, so the runtime manager can start, stop, or rebuild the live channel.
 */
public record ChannelRuntimeEvent(UUID channelId, boolean deleted) {}
