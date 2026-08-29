package io.okagent.module.mcp.application;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record McpToolCallRequest(@NotNull Map<String, Object> arguments) {}
