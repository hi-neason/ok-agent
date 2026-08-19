package io.okagent.service.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.domain.mcp.*;
import io.okagent.repository.mcp.*;
import io.okagent.service.model.ApiKeyCipher;
import io.okagent.web.mcp.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class McpServerServiceImpl implements McpServerService {
    private final McpServerRepository servers;
    private final McpToolSnapshotRepository tools;
    private final McpConnectionInspector inspector;
    private final ApiKeyCipher cipher;
    private final ObjectMapper json = new ObjectMapper();

    public McpServerServiceImpl(
            McpServerRepository servers,
            McpToolSnapshotRepository tools,
            McpConnectionInspector inspector,
            ApiKeyCipher cipher) {
        this.servers = servers;
        this.tools = tools;
        this.inspector = inspector;
        this.cipher = cipher;
    }

    @Override
    public List<McpServerResponse> list() {
        return servers.findAll().stream()
                .sorted(Comparator.comparing(McpServer::getUpdatedAt).reversed())
                .map(this::response)
                .toList();
    }

    @Override
    @Transactional
    public McpServerResponse create(McpServerRequest r) {
        if (servers.existsByServerKey(r.serverKey()))
            throw new IllegalArgumentException("MCP server key already exists");
        var s = new McpServer(
                UUID.randomUUID(),
                r.serverKey(),
                r.name(),
                text(r.description()),
                r.transport(),
                r.serverUrl(),
                r.command(),
                write(list(r.arguments())),
                write(map(r.queryParameters())),
                secrets(r),
                timeout(r.requestTimeoutSeconds(), 15),
                timeout(r.initializationTimeoutSeconds(), 10));
        return response(servers.save(s));
    }

    @Override
    @Transactional
    public McpServerResponse update(UUID id, McpServerRequest r) {
        var s = get(id);
        s.update(
                r.serverKey(),
                r.name(),
                text(r.description()),
                r.transport(),
                r.serverUrl(),
                r.command(),
                write(list(r.arguments())),
                write(map(r.queryParameters())),
                hasSecrets(r) ? secrets(r) : null,
                timeout(r.requestTimeoutSeconds(), 15),
                timeout(r.initializationTimeoutSeconds(), 10));
        return response(servers.save(s));
    }

    @Override
    @Transactional
    public McpServerResponse setEnabled(UUID id, boolean enabled) {
        var s = get(id);
        s.setEnabled(enabled);
        return response(servers.save(s));
    }

    @Override
    public void delete(UUID id) {
        servers.deleteById(id);
    }

    @Override
    @Transactional
    public McpInspectionResponse inspect(UUID id) {
        var s = get(id);
        try {
            var found = inspector.inspect(s, secret(s, "headers"), secret(s, "environment"));
            // Flush the delete before inserting so the unique key (mcp_server_id, tool_name)
            // is released before the new batch arrives.
            tools.deleteByServerId(id);
            tools.flush();
            // Some MCP servers expose duplicate tool names; collapse them to the first
            // occurrence to avoid violating the uk_mcp_server_tool unique constraint.
            var seen = new LinkedHashMap<String, McpToolSnapshot>();
            for (var t : found) {
                seen.putIfAbsent(t.name(), new McpToolSnapshot(id, t.name(), t.description(), t.inputSchemaJson()));
            }
            tools.saveAll(seen.values());
            s.recordTest(true, seen.size());
            return new McpInspectionResponse(true, "Connection succeeded", found);
        } catch (Exception e) {
            s.recordTest(false, s.getToolCount());
            return new McpInspectionResponse(false, safe(e), List.of());
        }
    }

    @Override
    public McpInspectionResponse inspect(McpServerRequest r) {
        var s = new McpServer(
                UUID.randomUUID(),
                r.serverKey(),
                r.name(),
                text(r.description()),
                r.transport(),
                r.serverUrl(),
                r.command(),
                write(list(r.arguments())),
                write(map(r.queryParameters())),
                null,
                timeout(r.requestTimeoutSeconds(), 15),
                timeout(r.initializationTimeoutSeconds(), 10));
        try {
            var found = inspector.inspect(s, map(r.headers()), map(r.environment()));
            return new McpInspectionResponse(true, "Connection succeeded", found);
        } catch (Exception e) {
            return new McpInspectionResponse(false, safe(e), List.of());
        }
    }

    @Override
    public List<McpToolResponse> tools(UUID id) {
        return tools.findByServerIdOrderByName(id).stream()
                .map(t -> new McpToolResponse(
                        t.getName(), t.getDescription(), t.getInputSchemaJson(), t.getDiscoveredAt()))
                .toList();
    }

    @Override
    public McpToolCallResponse callTool(UUID id, String toolName, McpToolCallRequest request) {
        var server = get(id);
        var startedAt = System.nanoTime();
        try {
            var result = inspector.callTool(
                    server, secret(server, "headers"), secret(server, "environment"), toolName, request.arguments());
            return new McpToolCallResponse(
                    result.success(),
                    result.success() ? "Tool call succeeded" : "Tool returned an error",
                    result.resultJson(),
                    elapsedMillis(startedAt));
        } catch (Exception exception) {
            return new McpToolCallResponse(false, safe(exception), "{}", elapsedMillis(startedAt));
        }
    }

    private McpServer get(UUID id) {
        return servers.findById(id).orElseThrow(() -> new NoSuchElementException("MCP server not found"));
    }

    private McpServerResponse response(McpServer s) {
        var sec = readSecrets(s);
        return new McpServerResponse(
                s.getId(),
                s.getServerKey(),
                s.getName(),
                s.getDescription(),
                s.getTransport(),
                s.getServerUrl(),
                s.getCommand(),
                readList(s.getArgumentsJson()),
                readMap(s.getQueryParametersJson()),
                secretKeys(sec, "headers"),
                secretKeys(sec, "environment"),
                s.isEnabled(),
                s.getRequestTimeoutSeconds(),
                s.getInitializationTimeoutSeconds(),
                s.getLastTestStatus(),
                s.getLastTestedAt(),
                s.getToolCount(),
                s.getUpdatedAt());
    }

    private String secrets(McpServerRequest r) {
        return cipher.encrypt(write(Map.of("headers", map(r.headers()), "environment", map(r.environment()))));
    }

    private boolean hasSecrets(McpServerRequest r) {
        return !map(r.headers()).isEmpty() || !map(r.environment()).isEmpty();
    }

    private Map<String, Object> readSecrets(McpServer s) {
        if (s.getSecretsCiphertext() == null) return Map.of();
        try {
            return json.readValue(cipher.decrypt(s.getSecretsCiphertext()), new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> secret(McpServer s, String key) {
        var value = readSecrets(s).get(key);
        return value instanceof Map<?, ?> m ? (Map<String, String>) m : Map.of();
    }

    private Set<String> secretKeys(Map<String, Object> values, String key) {
        var value = values.get(key);
        return value instanceof Map<?, ?> m
                ? new TreeSet<>(m.keySet().stream().map(String::valueOf).toList())
                : Set.of();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid MCP configuration", e);
        }
    }

    private List<String> readList(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, String> readMap(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<String> list(List<String> value) {
        return value == null ? List.of() : value;
    }

    private Map<String, String> map(Map<String, String> value) {
        return value == null ? Map.of() : value;
    }

    private int timeout(int value, int fallback) {
        return value <= 0 ? fallback : value;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String safe(Exception e) {
        var root = e;
        while (root.getCause() instanceof Exception cause) root = cause;
        var message = root.getMessage() == null ? "" : root.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("timeout") || message.contains("timed out")) {
            return "Connection timed out";
        }
        if (message.contains("401") || message.contains("403") || message.contains("unauthorized")) {
            return "Authentication failed";
        }
        return "Connection failed";
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
