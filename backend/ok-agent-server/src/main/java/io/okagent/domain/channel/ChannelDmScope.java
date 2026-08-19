package io.okagent.domain.channel;

/**
 * DM session-key resolution for a channel, mirroring {@code io.agentscope.harness.agent.gateway.channel.DmScope}.
 * PER_PEER gives each messaging user an independent Agent session and is the personal-channel default.
 */
public enum ChannelDmScope {
    MAIN,
    PER_PEER,
    PER_CHANNEL_PEER,
    PER_ACCOUNT_CHANNEL_PEER
}
