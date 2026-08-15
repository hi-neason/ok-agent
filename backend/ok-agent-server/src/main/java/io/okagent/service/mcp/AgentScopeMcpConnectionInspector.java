package io.okagent.service.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.mcp.*;
import io.okagent.domain.mcp.McpServer;
import io.okagent.web.mcp.McpToolResponse;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AgentScopeMcpConnectionInspector implements McpConnectionInspector {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<McpToolResponse> inspect(
            McpServer server, Map<String, String> headers, Map<String, String> environment) {
        try (McpClientWrapper client = client(server, headers, environment)) {
            client.initialize().block(Duration.ofSeconds(server.getInitializationTimeoutSeconds() + 1L));
            var tools = client.listTools().block(Duration.ofSeconds(server.getRequestTimeoutSeconds() + 1L));
            if (tools == null) return List.of();
            return tools.stream()
                    .map(tool -> new McpToolResponse(
                            tool.name(), tool.description(), json(tool.inputSchema()), java.time.Instant.now()))
                    .toList();
        }
    }

    @Override
    public McpToolInvocationResult callTool(
            McpServer server,
            Map<String, String> headers,
            Map<String, String> environment,
            String toolName,
            Map<String, Object> arguments) {
        try (McpClientWrapper client = client(server, headers, environment)) {
            client.initialize().block(Duration.ofSeconds(server.getInitializationTimeoutSeconds() + 1L));
            var result = client.callTool(toolName, arguments)
                    .block(Duration.ofSeconds(server.getRequestTimeoutSeconds() + 1L));
            if (result == null) throw new IllegalStateException("MCP tool returned no result");
            return new McpToolInvocationResult(!Boolean.TRUE.equals(result.isError()), json(result));
        }
    }

    private McpClientWrapper client(McpServer server, Map<String, String> headers, Map<String, String> environment) {
        McpClientBuilder builder = McpClientBuilder.create(server.getServerKey());
        switch (server.getTransport()) {
            case STREAMABLE_HTTP -> builder.streamableHttpTransport(required(server.getServerUrl(), "serverUrl"))
                    .customizeStreamableHttpClient(b -> b.version(java.net.http.HttpClient.Version.HTTP_1_1));
            case SSE -> builder.sseTransport(required(server.getServerUrl(), "serverUrl"));
            case STDIO -> builder.stdioTransport(
                    required(server.getCommand(), "command"), arguments(server), environment);
        }
        builder.headers(headers)
                .queryParams(map(server.getQueryParametersJson()))
                .timeout(Duration.ofSeconds(server.getRequestTimeoutSeconds()))
                .initializationTimeout(Duration.ofSeconds(server.getInitializationTimeoutSeconds()));
        return builder.buildSync();
    }

    private List<String> arguments(McpServer server) {
        try {
            return objectMapper.readValue(
                    server.getArgumentsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid arguments configuration", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> map(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid query parameters", e);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
