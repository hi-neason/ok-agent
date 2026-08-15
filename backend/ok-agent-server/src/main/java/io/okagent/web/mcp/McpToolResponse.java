package io.okagent.web.mcp;

import java.time.Instant;

public record McpToolResponse(String name, String description, String inputSchemaJson, Instant discoveredAt) {}
