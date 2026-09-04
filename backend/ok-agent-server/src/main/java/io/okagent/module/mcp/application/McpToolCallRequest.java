package io.okagent.module.mcp.application;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record McpToolCallRequest(@NotNull Map<String, Object> arguments) {
    public McpToolCallRequest {
        arguments = arguments == null ? null : Map.copyOf(arguments);
    }
}
