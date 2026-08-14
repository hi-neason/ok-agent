package io.okagent.service.mcp;

import io.okagent.domain.mcp.McpServer;
import io.okagent.web.mcp.McpToolResponse;
import java.util.*;

public interface McpConnectionInspector {
  /** Opens the configured MCP transport and returns the tools advertised by the server. */
  List<McpToolResponse> inspect(
      McpServer server, Map<String, String> headers, Map<String, String> environment);
}
