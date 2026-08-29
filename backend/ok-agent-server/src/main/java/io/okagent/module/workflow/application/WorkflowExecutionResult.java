package io.okagent.module.workflow.application;

/** Result of triggering one workflow execution in the source system. */
public record WorkflowExecutionResult(
        boolean success,
        String remoteRunId,
        String status,
        String outputSummary,
        String errorMessage,
        Double elapsedSeconds,
        Integer totalTokens) {

    public static WorkflowExecutionResult success(
            String remoteRunId, String outputSummary, Double elapsedSeconds, Integer totalTokens) {
        return new WorkflowExecutionResult(
                true, remoteRunId, "SUCCESS", outputSummary, null, elapsedSeconds, totalTokens);
    }

    public static WorkflowExecutionResult failure(String remoteRunId, String errorMessage) {
        return new WorkflowExecutionResult(false, remoteRunId, "ERROR", null, errorMessage, null, null);
    }
}
