package io.okagent.service.agent;

import io.okagent.domain.mcp.McpTransport;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Frozen non-secret MCP connection configuration plus its managed credential reference. */
public record ResolvedMcpServer(
        UUID assetId,
        String serverKey,
        McpTransport transport,
        String serverUrl,
        String command,
        List<String> arguments,
        Map<String, String> queryParameters,
        int requestTimeoutSeconds,
        int initializationTimeoutSeconds) {}
