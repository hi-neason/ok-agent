package io.okagent.module.customerchat.application;

import java.util.UUID;

/** Runtime-neutral input for one external customer chat message. */
public record CustomerChatCommand(
        UUID agentId, String channelId, String sessionId, String userId, String message) {}
