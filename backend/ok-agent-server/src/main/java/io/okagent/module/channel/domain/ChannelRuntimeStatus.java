package io.okagent.module.channel.domain;

/** Observable lifecycle state of a channel instance managed by ChannelRuntimeManager. */
public enum ChannelRuntimeStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}
