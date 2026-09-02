package io.okagent.module.workflow.application.dify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.okagent.module.workflow.application.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Dify workflow application provider.
 *
 * <p>A Dify app API key is scoped to a single application, so one source = one app and
 * {@link #listWorkflows} returns exactly one workflow. The {@code /v1/info} endpoint identifies the
 * app and its mode; only {@code mode=workflow} is supported (chatflow/agent/completion use different
 * APIs). {@code /v1/parameters} exposes the Start-node input variables which are converted to a JSON
 * Schema. {@code POST /v1/workflows/run} with {@code response_mode=blocking} runs synchronously.
 */
@Component
public class DifyWorkflowProvider implements WorkflowProvider {
    private static final Logger log = LoggerFactory.getLogger(DifyWorkflowProvider.class);
    private static final String SELF_WORKFLOW_ID = "self";
    private static final int OUTPUT_SUMMARY_LIMIT = 4000;

    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public DifyWorkflowProvider(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String type() {
        return "DIFY";
    }

    @Override
    public ConnectionTestResult test(WorkflowSourceConfig config) {
        try {
            var info = fetchInfo(config);
            String mode = info.path("mode").asText("");
            String name = info.path("name").asText("Dify app");
            if (!"workflow".equalsIgnoreCase(mode)) {
                return ConnectionTestResult.unsupported(
                        name, "Dify app mode '" + mode + "' is not supported; only 'workflow' apps can be run");
            }
            return ConnectionTestResult.ok(name, "Connected to Dify app '" + name + "' (mode=workflow)");
        } catch (Exception e) {
            return ConnectionTestResult.failed(safeMessage(e));
        }
    }

    @Override
    public List<RemoteWorkflowSummary> listWorkflows(WorkflowSourceConfig config) {
        try {
            var info = fetchInfo(config);
            return List.of(new RemoteWorkflowSummary(
                    SELF_WORKFLOW_ID,
                    info.path("name").asText("Dify workflow"),
                    true,
                    readTags(info),
                    info.path("description").asText(""),
                    info.path("mode").asText("workflow")));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list Dify workflows: " + safeMessage(e), e);
        }
    }

    @Override
    public RemoteWorkflowDetail describeRemote(WorkflowSourceConfig config, String remoteWorkflowId) {
        try {
            var info = fetchInfo(config);
            var parameters = fetchParameters(config);
            String name = info.path("name").asText("Dify workflow");
            String mode = info.path("mode").asText("workflow");
            String description = info.path("description").asText("");
            String schema = buildInputSchema(parameters.path("user_input_form"));
            ObjectNode raw = json.createObjectNode();
            raw.set("info", info);
            raw.set("parameters", parameters);
            return new RemoteWorkflowDetail(
                    SELF_WORKFLOW_ID,
                    name,
                    mode,
                    true,
                    readTags(info),
                    description,
                    schema,
                    json.writeValueAsString(raw));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to describe Dify workflow: " + safeMessage(e), e);
        }
    }

    @Override
    public WorkflowExecutionResult execute(
            WorkflowSourceConfig config, String remoteWorkflowId, Map<String, Object> inputs, String endUserId) {
        long started = System.nanoTime();
        try {
            ObjectNode body = json.createObjectNode();
            body.set("inputs", json.valueToTree(inputs == null ? Map.of() : inputs));
            body.put("response_mode", "blocking");
            body.put("user", endUserId == null || endUserId.isBlank() ? "ok-agent" : endUserId);

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl(config) + "/workflows/run"))
                    .timeout(Duration.ofSeconds(config.executeTimeoutSeconds()))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .header(HttpHeaders.AUTHORIZATION, bearer(config))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return WorkflowExecutionResult.failure(
                        null, "Dify returned HTTP " + response.statusCode() + ": " + truncate(response.body(), 500));
            }
            JsonNode root = json.readTree(response.body());
            JsonNode data = root.path("data");
            String status = data.path("status").asText("");
            String runId = firstNonBlank(root.path("workflow_run_id"), data.path("id"));
            Double elapsed =
                    data.has("elapsed_time") && !data.path("elapsed_time").isNull()
                            ? data.path("elapsed_time").asDouble()
                            : null;
            Integer tokens =
                    data.has("total_tokens") && !data.path("total_tokens").isNull()
                            ? data.path("total_tokens").asInt()
                            : null;

            if ("succeeded".equalsIgnoreCase(status)) {
                String summary = summariseOutputs(data.path("outputs"));
                log.info("Dify workflow succeeded: runId={} elapsed={}s tokens={}", runId, elapsed, tokens);
                return WorkflowExecutionResult.success(runId, summary, elapsed, tokens);
            }
            String error = data.path("error").isNull()
                    ? "Dify workflow ended with status '" + status + "'"
                    : data.path("error").asText();
            return WorkflowExecutionResult.failure(runId, error);
        } catch (Exception e) {
            log.warn("Dify workflow execution failed: {}", e.getMessage());
            return WorkflowExecutionResult.failure(null, safeMessage(e));
        } finally {
            log.debug("Dify execute took {} ms", (System.nanoTime() - started) / 1_000_000);
        }
    }

    private JsonNode fetchInfo(WorkflowSourceConfig config) throws Exception {
        return getJson(config, "/info");
    }

    private JsonNode fetchParameters(WorkflowSourceConfig config) throws Exception {
        return getJson(config, "/parameters?user=ok-agent");
    }

    private JsonNode getJson(WorkflowSourceConfig config, String path) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl(config) + path))
                .timeout(Duration.ofSeconds(Math.max(config.connectTimeoutSeconds(), 5)))
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.AUTHORIZATION, bearer(config))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(
                    "HTTP " + response.statusCode() + " from Dify: " + truncate(response.body(), 300));
        }
        return json.readTree(response.body());
    }

    /**
     * Converts Dify's {@code user_input_form} into a JSON Schema object. Each entry is a single-key
     * object whose key is the control type and value carries variable/label/required/options.
     */
    private String buildInputSchema(JsonNode userInputForm) throws Exception {
        ObjectNode schema = json.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = json.createObjectNode();
        ArrayNode required = json.createArrayNode();

        if (userInputForm != null && userInputForm.isArray()) {
            for (JsonNode entry : userInputForm) {
                entry.fields().forEachRemaining(field -> {
                    JsonNode def = field.getValue();
                    String variable = def.path("variable").asText("");
                    if (variable.isBlank()) return;
                    ObjectNode prop = json.createObjectNode();
                    prop.put("type", jsonTypeFor(field.getKey(), def));
                    String label = def.path("label").asText("");
                    if (!label.isBlank()) prop.put("description", label);
                    if (def.has("options")
                            && def.path("options").isArray()
                            && !def.path("options").isEmpty()) {
                        prop.set("enum", def.path("options"));
                    }
                    if (def.has("default") && !def.path("default").isNull()) {
                        prop.set("default", def.path("default"));
                    }
                    properties.set(variable, prop);
                    if (def.path("required").asBoolean(false)) {
                        required.add(variable);
                    }
                });
            }
        }
        schema.set("properties", properties);
        schema.set("required", required);
        schema.put("additionalProperties", false);
        return json.writeValueAsString(schema);
    }

    private String jsonTypeFor(String controlType, JsonNode def) {
        return switch (controlType) {
            case "number" -> "number";
            case "select" -> def.path("options").isArray() ? "string" : "string";
            default -> "string";
        };
    }

    private String summariseOutputs(JsonNode outputs) {
        if (outputs == null || outputs.isNull() || outputs.isEmpty()) {
            return "(workflow returned no outputs)";
        }
        try {
            return truncate(json.writeValueAsString(outputs), OUTPUT_SUMMARY_LIMIT);
        } catch (Exception e) {
            return truncate(outputs.toString(), OUTPUT_SUMMARY_LIMIT);
        }
    }

    private List<String> readTags(JsonNode info) {
        var tags = info.path("tags");
        if (!tags.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        tags.forEach(t -> result.add(t.asText()));
        return result;
    }

    private String bearer(WorkflowSourceConfig config) {
        String key = config.secret("apiKey");
        if (key.isBlank()) throw new IllegalStateException("Dify API key is not configured");
        return "Bearer " + key;
    }

    private String baseUrl(WorkflowSourceConfig config) {
        String base = config.baseUrl() == null ? "" : config.baseUrl().trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.isEmpty()) throw new IllegalStateException("Dify base URL is not configured");
        return base;
    }

    private String firstNonBlank(JsonNode... nodes) {
        for (JsonNode n : nodes) {
            if (n != null && !n.isNull()) {
                String s = n.asText("");
                if (!s.isBlank()) return s;
            }
        }
        return null;
    }

    private String truncate(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit) + "...(truncated)";
    }

    private String safeMessage(Exception e) {
        var root = e;
        while (root.getCause() instanceof Exception cause && cause != root) root = cause;
        String message = root.getMessage();
        if (message == null || message.isBlank()) return root.getClass().getSimpleName();
        if (message.contains("401") || message.toLowerCase().contains("unauthorized")) {
            return "Authentication failed: check the Dify API key";
        }
        return message;
    }
}
