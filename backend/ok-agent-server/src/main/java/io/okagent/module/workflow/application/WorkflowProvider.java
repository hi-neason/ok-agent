package io.okagent.module.workflow.application;

import java.util.List;
import java.util.Map;

/**
 * Service provider interface for an external workflow system (Dify, n8n, ...). Implementations are
 * discovered by Spring and routed by {@link #type()}. Adding a system = adding a component, the
 * catalog/runtime layer does not change.
 *
 * <p>The number of workflows per source is implementation-specific: an instance-scoped system
 * (n8n) may return many; an app-scoped system (Dify, one API key per app) returns one. The SPI
 * always speaks in lists so callers stay uniform.
 */
public interface WorkflowProvider {

    /** SPI type key, matching {@code WorkflowSourceType}, e.g. "dify". */
    String type();

    /** Tests connectivity and identifies the remote application. */
    ConnectionTestResult test(WorkflowSourceConfig config);

    /** Lists the workflows this source exposes (lightweight, no input schema). */
    List<RemoteWorkflowSummary> listWorkflows(WorkflowSourceConfig config);

    /** Fetches metadata and the input schema for one remote workflow. */
    RemoteWorkflowDetail describeRemote(WorkflowSourceConfig config, String remoteWorkflowId);

    /** Triggers a workflow synchronously and returns its result. */
    WorkflowExecutionResult execute(
            WorkflowSourceConfig config, String remoteWorkflowId, Map<String, Object> inputs, String endUserId);
}
