package io.okagent.service.product;

/** Result of testing an external product source connection. */
public record ConnectionTestResult(boolean success, String message) {}
