package io.okagent.domain.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Immutable audit record of one workflow execution triggered by an agent at runtime. */
@Entity
@Table(name = "workflow_execution_audit")
public class WorkflowExecutionAudit {
    @Id
    private UUID id;

    @Column(name = "agent_id")
    private UUID agentId;

    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "catalog_item_id")
    private UUID catalogItemId;

    @Column(name = "inputs_hash", nullable = false, length = 64)
    private String inputsHash;

    @Column(name = "remote_run_id", length = 255)
    private String remoteRunId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "elapsed_seconds")
    private Double elapsedSeconds;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WorkflowExecutionAudit() {}

    public WorkflowExecutionAudit(
            UUID id,
            UUID agentId,
            String userId,
            String sessionId,
            UUID sourceId,
            UUID catalogItemId,
            String inputsHash,
            String remoteRunId,
            String status,
            String resultSummary,
            String errorMessage,
            Double elapsedSeconds,
            Integer totalTokens,
            int latencyMs) {
        this.id = id;
        this.agentId = agentId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.sourceId = sourceId;
        this.catalogItemId = catalogItemId;
        this.inputsHash = inputsHash;
        this.remoteRunId = remoteRunId;
        this.status = status;
        this.resultSummary = resultSummary;
        this.errorMessage = errorMessage;
        this.elapsedSeconds = elapsedSeconds;
        this.totalTokens = totalTokens;
        this.latencyMs = latencyMs;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAgentId() { return agentId; }
    public String getUserId() { return userId; }
    public String getSessionId() { return sessionId; }
    public UUID getSourceId() { return sourceId; }
    public UUID getCatalogItemId() { return catalogItemId; }
    public String getInputsHash() { return inputsHash; }
    public String getRemoteRunId() { return remoteRunId; }
    public String getStatus() { return status; }
    public String getResultSummary() { return resultSummary; }
    public String getErrorMessage() { return errorMessage; }
    public Double getElapsedSeconds() { return elapsedSeconds; }
    public Integer getTotalTokens() { return totalTokens; }
    public int getLatencyMs() { return latencyMs; }
    public Instant getCreatedAt() { return createdAt; }
}
