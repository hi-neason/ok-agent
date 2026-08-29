package io.okagent.module.mcp.application;

import java.util.List;

public record McpInspectionResponse(boolean success, String message, List<McpToolResponse> tools) {}
