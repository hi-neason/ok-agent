package io.okagent.service.intent;

/** Result of intent classification: which intent a query maps to and how confident we are. */
public record IntentClassification(
        String intentKey,
        String intentName,
        double confidence,
        String targetSubagentKey,
        boolean fallback) {}
