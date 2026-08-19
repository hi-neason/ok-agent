package io.okagent.web.chat;

import java.util.UUID;

/** Production chat entry (intent-routed). Unlike the debug endpoint, the caller does not pick an
 *  agent — the router agent is chosen by {@code agentId} and the sub-agent by the detected intent. */
public record ProductionChatRequest(UUID agentId, String channelId, String sessionId, String userId, String message) {}
