package io.okagent.module.intent.application;

/** Result of intent classification: which intent a query maps to and how confident we are. */
public record IntentClassification(
        String intentKey, String intentName, Double confidence, String targetSubagentKey, boolean fallback) {}
