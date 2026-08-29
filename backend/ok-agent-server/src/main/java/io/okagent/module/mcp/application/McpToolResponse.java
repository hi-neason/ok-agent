package io.okagent.module.mcp.application;

import java.time.Instant;

public record McpToolResponse(String name, String description, String inputSchemaJson, Instant discoveredAt) {}
