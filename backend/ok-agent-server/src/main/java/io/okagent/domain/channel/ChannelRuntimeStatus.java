package io.okagent.domain.channel;

/** Observable lifecycle state of a channel instance managed by ChannelRuntimeManager. */
public enum ChannelRuntimeStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}
