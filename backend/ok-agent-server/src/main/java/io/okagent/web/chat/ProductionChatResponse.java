package io.okagent.web.chat;

/** Production chat reply plus the routing decision that produced it (for UI observability). */
public record ProductionChatResponse(
        String sessionId,
        String reply,
        String intentKey,
        String intentName,
        double confidence,
        String targetSubagentKey,
        boolean fallback) {}
