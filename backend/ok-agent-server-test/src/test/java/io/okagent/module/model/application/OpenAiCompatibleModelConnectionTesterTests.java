package io.okagent.module.model.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.okagent.module.model.domain.ModelAsset;
import io.okagent.module.model.domain.ModelType;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleModelConnectionTesterTests {
    private HttpServer server;
    private String baseUrl;
    private final io.agentscope.core.model.transport.HttpTransport transport =
            io.agentscope.core.model.transport.OkHttpTransport.builder().build();

    @BeforeEach
    void startProviderStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        server.start();
    }

    @AfterEach
    void stopProviderStub() {
        server.stop(0);
    }

    @Test
    void shouldTestConnectionThroughAgentScopeOpenAiModel() {
        var authorization = new AtomicReference<String>();
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(
                    exchange,
                    200,
                    """
              {
                "id": "chatcmpl-test",
                "object": "chat.completion",
                "created": 1,
                "model": "test-model",
                "choices": [{
                  "index": 0,
                  "message": {"role": "assistant", "content": "OK"},
                  "finish_reason": "stop"
                }],
                "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}
              }
              """);
        });
        var asset = new ModelAsset(
                UUID.randomUUID(), "Test model", ModelType.LLM, "Custom", "test-model", baseUrl, "encrypted", true);

        var result = new OpenAiCompatibleModelConnectionTester(transport).test(asset, "test-api-key");

        assertThat(result.success()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(authorization.get()).isEqualTo("Bearer test-api-key");
    }

    @Test
    void shouldExplainAuthenticationFailureWithoutExposingProviderResponse() {
        server.createContext(
                "/v1/chat/completions",
                exchange -> respond(exchange, 401, "{\"error\":{\"message\":\"secret detail\"}}"));
        var asset = new ModelAsset(
                UUID.randomUUID(), "Test model", ModelType.LLM, "Custom", "test-model", baseUrl, "encrypted", true);

        var result = new OpenAiCompatibleModelConnectionTester(transport).test(asset, "invalid-api-key");

        assertThat(result.success()).isFalse();
        assertThat(result.statusCode()).isEqualTo(401);
        assertThat(result.message()).isEqualTo("API_KEY authentication failed.");
        assertThat(result.message()).doesNotContain("secret detail");
    }

    private void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
