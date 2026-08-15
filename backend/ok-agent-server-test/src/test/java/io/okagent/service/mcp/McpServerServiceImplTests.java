package io.okagent.service.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.okagent.domain.mcp.McpServer;
import io.okagent.domain.mcp.McpTransport;
import io.okagent.repository.mcp.McpServerRepository;
import io.okagent.repository.mcp.McpToolSnapshotRepository;
import io.okagent.service.model.ApiKeyCipher;
import io.okagent.web.mcp.McpServerRequest;
import io.okagent.web.mcp.McpToolCallRequest;
import io.okagent.web.mcp.McpToolResponse;
import java.util.*;
import org.junit.jupiter.api.Test;

class McpServerServiceImplTests {
    @Test
    void shouldCreateAndInspectServerWithoutReturningSecretValues() {
        var servers = mock(McpServerRepository.class);
        var tools = mock(McpToolSnapshotRepository.class);
        var inspector = mock(McpConnectionInspector.class);
        when(servers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inspector.inspect(any(), any(), any()))
                .thenReturn(List.of(new McpToolResponse("search", "Search", "{}", null)));
        var service = new McpServerServiceImpl(servers, tools, inspector, new ApiKeyCipher("test-encryption-key"));
        var request = new McpServerRequest(
                "demo",
                "Demo",
                "",
                McpTransport.STREAMABLE_HTTP,
                "http://localhost/mcp",
                null,
                List.of(),
                Map.of("Authorization", "Bearer secret"),
                Map.of(),
                Map.of(),
                5,
                5);

        var created = service.create(request);
        var inspected = service.inspect(request);

        assertThat(created.configuredHeaderNames()).containsExactly("Authorization");
        assertThat(created.toString()).doesNotContain("Bearer secret");
        assertThat(inspected.success()).isTrue();
        assertThat(inspected.tools()).extracting(McpToolResponse::name).containsExactly("search");
    }

    @Test
    void shouldInvokeToolThroughSavedServer() {
        var servers = mock(McpServerRepository.class);
        var toolSnapshots = mock(McpToolSnapshotRepository.class);
        var inspector = mock(McpConnectionInspector.class);
        var id = UUID.randomUUID();
        var server = new McpServer(
                id, "demo", "Demo", "", McpTransport.SSE, "http://localhost/sse", null, "[]", "{}", null, 5, 5);
        when(servers.findById(id)).thenReturn(Optional.of(server));
        when(inspector.callTool(any(), any(), any(), eq("add"), any()))
                .thenReturn(new McpToolInvocationResult(true, "{\"content\":\"3\"}"));
        var service =
                new McpServerServiceImpl(servers, toolSnapshots, inspector, new ApiKeyCipher("test-encryption-key"));

        var response = service.callTool(id, "add", new McpToolCallRequest(Map.of("a", 1, "b", 2)));

        assertThat(response.success()).isTrue();
        assertThat(response.resultJson()).contains("3");
    }
}
