package io.okagent.module.product.application;

/** Result of testing an external product source connection. */
public record ConnectionTestResult(boolean success, String message) {}
