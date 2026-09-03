package io.okagent.module.workflow.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The fixed set of tools exposed to the LLM for running external workflows. One instance is built
 * per agent (holding its agentId), so the catalog can authorize every call against that agent's
 * bindings. The three tools follow a discover-then-run protocol: list → describe → start.
 */
public class WorkflowTools {
    private static final Logger log = LoggerFactory.getLogger(WorkflowTools.class);

    private final WorkflowRuntimeCatalog catalog;
    private final UUID agentId;
    private final ObjectMapper json = new ObjectMapper();

    public WorkflowTools(WorkflowRuntimeCatalog catalog, UUID agentId) {
        this.catalog = catalog;
        this.agentId = agentId;
    }

    @Tool(
            name = "list_workflows",
            readOnly = true,
            description = "List the external workflows available to this agent. Each entry has an id, a"
                    + " name and a one-line description of when to use it. Call this first when"
                    + " the user wants to run/trigger/execute a workflow, and pick the best match.")
    public String listWorkflows(RuntimeContext ctx) {
        try {
            List<BoundWorkflow> workflows = catalog.listForAgent(agentId);
            if (workflows.isEmpty()) {
                return "No external workflows are bound to this agent.";
            }
            var sb = new StringBuilder();
            for (var w : workflows) {
                sb.append("- id: ")
                        .append(w.catalogItemId())
                        .append("\n  name: ")
                        .append(w.name())
                        .append("\n  source: ")
                        .append(w.sourceKey())
                        .append("\n  description: ")
                        .append(safe(w.description()))
                        .append('\n');
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("list_workflows failed for agent {}: {}", agentId, e.getMessage(), e);
            return "Error listing workflows: " + e.getMessage();
        }
    }

    @Tool(
            name = "describe_workflow",
            readOnly = true,
            description = "Get the input parameter schema (JSON Schema) of a workflow. You MUST call this"
                    + " before start_workflow so you know exactly which parameters to provide"
                    + " and their types. Do not invent parameters that are not in the schema.")
    public String describeWorkflow(
            RuntimeContext ctx,
            @ToolParam(name = "workflowId", description = "The catalog item id from list_workflows")
                    String workflowId) {
        try {
            var itemId = parseId(workflowId);
            var detail = catalog.describe(agentId, itemId);
            var sb = new StringBuilder();
            sb.append("name: ").append(detail.name()).append('\n');
            sb.append("input_schema:\n").append(detail.inputSchemaJson());
            if (detail.parameterDefaultsJson() != null
                    && !detail.parameterDefaultsJson().isBlank()) {
                sb.append("\nparameter_defaults:\n").append(detail.parameterDefaultsJson());
            }
            return sb.toString();
        } catch (SecurityException e) {
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            log.warn("describe_workflow failed for agent {}: {}", agentId, e.getMessage(), e);
            return "Error describing workflow: " + e.getMessage();
        }
    }

    @Tool(
            name = "start_workflow",
            description = "Start an external workflow and wait for its result. Provide inputs as a JSON"
                    + " object that matches the schema from describe_workflow. Returns the"
                    + " workflow output on success or an error message on failure; do not retry"
                    + " the same workflow with different parameters unless the user asks.")
    public String startWorkflow(
            RuntimeContext ctx,
            @ToolParam(name = "workflowId", description = "The catalog item id from list_workflows") String workflowId,
            @ToolParam(name = "inputs", description = "JSON object with the workflow input parameters")
                    String inputsJson) {
        try {
            var itemId = parseId(workflowId);
            Map<String, Object> inputs = parseInputs(inputsJson);
            String userId = ctx == null ? null : ctx.getUserId();
            String sessionId = ctx == null ? null : ctx.getSessionId();
            var result = catalog.execute(agentId, itemId, inputs, userId, sessionId);
            if (result.success()) {
                return "Workflow completed.\n" + result.message();
            }
            if ("DUPLICATE".equals(result.status())) {
                return "This workflow was already triggered with the same inputs; skipping duplicate.";
            }
            return "Workflow failed: " + result.message();
        } catch (SecurityException e) {
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            log.warn("start_workflow failed for agent {}: {}", agentId, e.getMessage(), e);
            return "Error starting workflow: " + e.getMessage();
        }
    }

    private UUID parseId(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid workflowId: " + value);
        }
    }

    private Map<String, Object> parseInputs(String inputsJson) throws Exception {
        if (inputsJson == null || inputsJson.isBlank()) {
            return Map.of();
        }
        return json.readValue(inputsJson, new TypeReference<>() {});
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
