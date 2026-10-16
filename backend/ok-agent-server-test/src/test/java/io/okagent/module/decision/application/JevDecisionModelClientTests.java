package io.okagent.module.decision.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class JevDecisionModelClientTests {
    private static final List<DecisionModelClient.Candidate> CANDIDATES =
            List.of(new DecisionModelClient.Candidate("sales", "Sales", "Purchasing support"));
    private final ObjectMapper json = new ObjectMapper();
    private final JevDecisionModelClient client = new JevDecisionModelClient();
    private HttpServer server;
    private ExecutorService executor;
    private String baseUrl;

    @BeforeEach
    void startProviderStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopProviderStub() {
        server.stop(0);
        executor.shutdownNow();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/", "/v1", "/v1/", "/v1/systemone", "/v1/systemone/"})
    void sendsDocumentedSchemaAndBearerAuthenticationToSystemOne(String suffix) {
        var request = new AtomicReference<JsonNode>();
        var method = new AtomicReference<String>();
        var path = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        var contentType = new AtomicReference<String>();
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            request.set(json.readTree(exchange.getRequestBody()));
            respond(exchange, 200, answer("sales", "0.9"));
        });

        var decision = choose(suffix);

        assertThat(decision).isEqualTo(new DecisionModelClient.Decision("sales", 0.9));
        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/v1/systemone");
        assertThat(authorization.get()).isEqualTo("Bearer test-api-key");
        assertThat(contentType.get()).isEqualTo("application/json");
        assertThat(request.get().size()).isEqualTo(3);
        assertThat(request.get().path("model").asText()).isEqualTo("test-model");
        assertThat(request.get().path("state").path("query").asText()).isEqualTo("Help me buy");
        var route = request.get().path("questions").path("route");
        assertThat(route.path("type").asText()).isEqualTo("choice");
        assertThat(route.path("instructions").asText()).isNotBlank();
        assertThat(route.path("criteria").size()).isEqualTo(2);
        assertThat(route.path("criteria").path("sales").asText()).isEqualTo("Sales: Purchasing support");
        assertThat(route.path("criteria").path("__okagent_no_match__").asText()).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1"})
    void acceptsConfidenceBoundaryValues(String confidence) {
        stub(200, answer("sales", confidence));
        assertThat(choose("").confidence()).isEqualTo(Double.valueOf(confidence));
    }

    @Test
    void returnsExplicitNoMatchWithoutDiscardingConfidence() {
        stub(200, answer("__okagent_no_match__", "0.8"));
        assertThat(choose("")).isEqualTo(new DecisionModelClient.Decision(null, 0.8));
    }

    @ParameterizedTest
    @CsvSource({"401, AUTHENTICATION", "403, AUTHENTICATION", "429, RATE_LIMIT", "529, PROVIDER"})
    void classifiesProviderErrorsWithoutExposingResponse(int status, DecisionModelException.Kind kind) {
        stub(status, "private-provider-body test-api-key Help me buy");
        assertThatThrownBy(() -> choose("")).isInstanceOfSatisfying(DecisionModelException.class, failure -> {
            assertThat(failure.kind()).isEqualTo(kind);
            assertThat(failure.statusCode()).isEqualTo(status);
            assertThat(failure.getMessage()).doesNotContain("private-provider-body", "test-api-key", "Help me buy");
            assertThat(failure.getCause()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "   ",
                "not-json",
                "{}",
                "null",
                "{\"answers\":{\"route\":{\"type\":\"choice\",\"choice\":\"sales\"}}}",
                "{\"answers\":{\"route\":{\"type\":\"boolean\",\"choice\":\"sales\",\"confidence\":0.9}}}"
            })
    void rejectsMalformedOrIncompleteAnswers(String response) {
        stub(200, response);
        assertFailure(DecisionModelException.Kind.INVALID_RESPONSE);
    }

    @Test
    void rejectsUnknownChoice() {
        stub(200, answer("unconfigured", "0.9"));
        assertFailure(DecisionModelException.Kind.INVALID_RESPONSE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"0.9\"", "true", "-0.01", "1.01", "1e309"})
    void rejectsInvalidConfidence(String confidence) {
        stub(200, answer("sales", confidence));
        assertFailure(DecisionModelException.Kind.INVALID_RESPONSE);
    }

    @Test
    void classifiesTimeoutWhenProviderDoesNotRespond() throws InterruptedException {
        var received = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        server.createContext("/", exchange -> {
            received.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        try {
            assertThatThrownBy(() -> client.choose(
                            baseUrl, "test-model", "test-api-key", "Help me buy", CANDIDATES, Duration.ofMillis(500)))
                    .isInstanceOfSatisfying(DecisionModelException.class, failure -> assertThat(failure.kind())
                            .isEqualTo(DecisionModelException.Kind.TIMEOUT));
            assertThat(received.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
        }
    }

    @Test
    void rejectsMoreThan254CandidatesBeforeCallingProvider() {
        var requested = new java.util.concurrent.atomic.AtomicBoolean();
        server.createContext("/", exchange -> {
            requested.set(true);
            respond(exchange, 200, answer("sales", "0.9"));
        });
        var candidates = IntStream.range(0, 255)
                .mapToObj(i -> new DecisionModelClient.Candidate("key" + i, "Name", "Description"))
                .toList();
        assertThatThrownBy(() -> client.choose(
                        baseUrl, "test-model", "test-api-key", "Help me buy", candidates, Duration.ofSeconds(2)))
                .isInstanceOfSatisfying(DecisionModelException.class, failure -> assertThat(failure.kind())
                        .isEqualTo(DecisionModelException.Kind.CONFIGURATION));
        assertThat(requested).isFalse();
    }

    private DecisionModelClient.Decision choose(String suffix) {
        return client.choose(
                baseUrl + suffix, "test-model", "test-api-key", "Help me buy", CANDIDATES, Duration.ofSeconds(2));
    }

    private void assertFailure(DecisionModelException.Kind kind) {
        assertThatThrownBy(() -> choose(""))
                .isInstanceOfSatisfying(DecisionModelException.class, failure -> assertThat(failure.kind())
                        .isEqualTo(kind));
    }

    private void stub(int status, String body) {
        server.createContext("/", exchange -> respond(exchange, status, body));
    }

    private String answer(String choice, String confidence) {
        return "{\"answers\":{\"route\":{\"type\":\"choice\",\"choice\":\"" + choice + "\",\"confidence\":" + confidence
                + ",\"probabilities\":{\"" + choice + "\":0.9}}}}";
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
