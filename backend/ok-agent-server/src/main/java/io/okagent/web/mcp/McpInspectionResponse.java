package io.okagent.web.mcp;

import java.util.List;

public record McpInspectionResponse(boolean success, String message, List<McpToolResponse> tools) {}
