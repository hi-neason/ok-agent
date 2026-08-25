package io.okagent.module.customerchat.api;

import io.okagent.module.customerchat.application.CustomerChatResult;

/** Production chat reply plus the routing decision that produced it (for UI observability). */
public record CustomerChatResponse(
        String sessionId,
        String reply,
        String intentKey,
        String intentName,
        double confidence,
        String targetSubagentKey,
        boolean fallback) {
    static CustomerChatResponse from(CustomerChatResult result) {
        return new CustomerChatResponse(
                result.sessionId(),
                result.reply(),
                result.intentKey(),
                result.intentName(),
                result.confidence(),
                result.targetSubagentKey(),
                result.fallback());
    }
}
