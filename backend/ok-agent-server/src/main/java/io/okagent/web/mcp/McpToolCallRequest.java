package io.okagent.web.mcp;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record McpToolCallRequest(@NotNull Map<String, Object> arguments) {}
