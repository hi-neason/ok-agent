package io.okagent.module.customerchat.application;

/** Customer-facing reply together with the routing decision used for observability. */
public record CustomerChatResult(
        String sessionId,
        String reply,
        String intentKey,
        String intentName,
        double confidence,
        String targetSubagentKey,
        boolean fallback) {}
