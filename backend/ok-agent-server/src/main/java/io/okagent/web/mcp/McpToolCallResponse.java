package io.okagent.web.mcp;

public record McpToolCallResponse(
    boolean success, String message, String resultJson, long durationMs) {}
