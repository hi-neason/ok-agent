package io.okagent.module.mcp.application;

public record McpToolCallResponse(boolean success, String message, String resultJson, long durationMs) {}
