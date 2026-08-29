package io.okagent.module.mcp.application;

import io.okagent.module.mcp.domain.McpServer;
import io.okagent.module.mcp.application.McpToolResponse;
import java.util.*;

public interface McpConnectionInspector {
    /** Opens the configured MCP transport and returns the tools advertised by the server. */
    List<McpToolResponse> inspect(McpServer server, Map<String, String> headers, Map<String, String> environment);

    /** Opens the configured MCP transport and invokes one advertised tool. */
    McpToolInvocationResult callTool(
            McpServer server,
            Map<String, String> headers,
            Map<String, String> environment,
            String toolName,
            Map<String, Object> arguments);
}
