package io.okagent.service.mcp;

import io.okagent.web.mcp.*;
import java.util.*;

public interface McpServerService {
    /** Lists all reusable MCP server configurations. */
    List<McpServerResponse> list();

    /** Creates a reusable MCP server configuration. */
    McpServerResponse create(McpServerRequest request);

    /** Updates an existing MCP server configuration while preserving omitted secrets. */
    McpServerResponse update(UUID id, McpServerRequest request);

    /** Enables or disables an MCP server for Agent references. */
    McpServerResponse setEnabled(UUID id, boolean enabled);

    /** Deletes an MCP server and its discovered tool snapshot. */
    void delete(UUID id);

    /** Tests a saved connection and refreshes its discovered tool snapshot. */
    McpInspectionResponse inspect(UUID id);

    /** Tests an unsaved connection without persisting its configuration. */
    McpInspectionResponse inspect(McpServerRequest request);

    /** Returns the latest discovered tool snapshot for a server. */
    List<McpToolResponse> tools(UUID id);

    /** Invokes a tool through a saved MCP server configuration for development debugging. */
    McpToolCallResponse callTool(UUID id, String toolName, McpToolCallRequest request);
}
