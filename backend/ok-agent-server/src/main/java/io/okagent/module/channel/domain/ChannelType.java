package io.okagent.module.channel.domain;

/** Supported external messaging providers. Only FEISHU is implemented in the MVP. */
public enum ChannelType {
    FEISHU,
    DINGTALK,
    WECOM,
    WECHAT
}
