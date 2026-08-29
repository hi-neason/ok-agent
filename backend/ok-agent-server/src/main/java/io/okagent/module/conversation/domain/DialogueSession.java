package io.okagent.module.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A persisted conversation session. This is the shared, runtime-agnostic record of a dialogue
 * between a user and an agent, produced both by the debug runtime and (in the future) by real
 * runtime instances. It is intentionally not named after "debug" so the same store serves every
 * producer.
 */
@Entity
@Table(name = "dialogue_session")
public class DialogueSession {

    @Id
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    /** The release serving the session (production only; null for debug sessions). */
    @Column(name = "release_id")
    private UUID releaseId;

    /** The agent version (vN) serving the session (production only). */
    @Column(name = "version_no")
    private Integer versionNo;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "next_turn_seq", nullable = false)
    private int nextTurnSeq = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_status", nullable = false, length = 24)
    private DialogueWorkStatus workStatus = DialogueWorkStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private DialoguePriority priority = DialoguePriority.NORMAL;

    @Column(name = "priority_rank", nullable = false)
    private int priorityRank = priorityRank(DialoguePriority.NORMAL);

    @Column(name = "assignee_account_id")
    private UUID assigneeAccountId;

    @Column(name = "handoff_requested_at")
    private Instant handoffRequestedAt;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "work_item_updated_by")
    private UUID workItemUpdatedBy;

    @Column(name = "work_item_updated_at", nullable = false)
    private Instant workItemUpdatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public DialogueSession() {}

    public DialogueSession(String sessionId, UUID agentId, String title, String userId, Instant createdAt) {
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.title = title;
        this.userId = userId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.workItemUpdatedAt = createdAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public UUID getAgentId() {
        return agentId;
    }

    public UUID getReleaseId() {
        return releaseId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setReleaseInfo(UUID releaseId, Integer versionNo) {
        this.releaseId = releaseId;
        this.versionNo = versionNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getNextTurnSeq() {
        return nextTurnSeq;
    }

    public int allocateNextTurnSeq() {
        if (nextTurnSeq == Integer.MAX_VALUE) {
            throw new IllegalStateException("Dialogue session sequence is exhausted");
        }
        return nextTurnSeq++;
    }

    public DialogueWorkStatus getWorkStatus() {
        return workStatus;
    }

    public DialoguePriority getPriority() {
        return priority;
    }

    public UUID getAssigneeAccountId() {
        return assigneeAccountId;
    }

    public Instant getHandoffRequestedAt() {
        return handoffRequestedAt;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public UUID getWorkItemUpdatedBy() {
        return workItemUpdatedBy;
    }

    public Instant getWorkItemUpdatedAt() {
        return workItemUpdatedAt;
    }

    public long getRowVersion() {
        return rowVersion;
    }

    /** Marks this conversation as awaiting a human operator. */
    public void requestHumanHandoff(DialoguePriority requestedPriority, UUID actorId, Instant now) {
        if (workStatus == DialogueWorkStatus.RESOLVED || workStatus == DialogueWorkStatus.CLOSED) {
            throw new IllegalStateException("TERMINAL_CONVERSATION_CANNOT_REQUEST_HANDOFF");
        }
        workStatus = DialogueWorkStatus.WAITING_HUMAN;
        priority = requestedPriority == null ? priority : requestedPriority;
        priorityRank = priorityRank(priority);
        handoffRequestedAt = handoffRequestedAt == null ? now : handoffRequestedAt;
        markWorkItemChanged(actorId, now);
    }

    /** Assigns or unassigns the conversation to a console account. */
    public void assign(UUID accountId, UUID actorId, Instant now) {
        if (workStatus == DialogueWorkStatus.CLOSED) {
            throw new IllegalStateException("CLOSED_CONVERSATION_CANNOT_BE_ASSIGNED");
        }
        assigneeAccountId = accountId;
        assignedAt = accountId == null ? null : now;
        if (accountId != null
                && (workStatus == DialogueWorkStatus.OPEN || workStatus == DialogueWorkStatus.WAITING_HUMAN)) {
            workStatus = DialogueWorkStatus.IN_PROGRESS;
        } else if (accountId == null && workStatus == DialogueWorkStatus.IN_PROGRESS) {
            workStatus = DialogueWorkStatus.WAITING_HUMAN;
        }
        markWorkItemChanged(actorId, now);
    }

    /** Changes operational priority without changing assignment or lifecycle state. */
    public void changePriority(DialoguePriority next, UUID actorId, Instant now) {
        priority = java.util.Objects.requireNonNull(next, "priority");
        priorityRank = priorityRank(priority);
        markWorkItemChanged(actorId, now);
    }

    /** Applies a validated lifecycle transition and maintains terminal timestamps. */
    public void transitionTo(DialogueWorkStatus next, UUID actorId, Instant now) {
        java.util.Objects.requireNonNull(next, "status");
        if (next == workStatus) {
            return;
        }
        if (!allowed(workStatus, next)) {
            throw new IllegalStateException("INVALID_DIALOGUE_TRANSITION:" + workStatus + "->" + next);
        }
        workStatus = next;
        if (next == DialogueWorkStatus.RESOLVED) {
            resolvedAt = now;
            closedAt = null;
        } else if (next == DialogueWorkStatus.CLOSED) {
            closedAt = now;
        } else {
            resolvedAt = null;
            closedAt = null;
        }
        markWorkItemChanged(actorId, now);
    }

    private boolean allowed(DialogueWorkStatus from, DialogueWorkStatus to) {
        return switch (from) {
            case OPEN -> to == DialogueWorkStatus.WAITING_HUMAN
                    || to == DialogueWorkStatus.IN_PROGRESS
                    || to == DialogueWorkStatus.RESOLVED
                    || to == DialogueWorkStatus.CLOSED;
            case WAITING_HUMAN -> to == DialogueWorkStatus.IN_PROGRESS
                    || to == DialogueWorkStatus.RESOLVED
                    || to == DialogueWorkStatus.CLOSED;
            case IN_PROGRESS -> to == DialogueWorkStatus.WAITING_CUSTOMER
                    || to == DialogueWorkStatus.WAITING_HUMAN
                    || to == DialogueWorkStatus.RESOLVED
                    || to == DialogueWorkStatus.CLOSED;
            case WAITING_CUSTOMER -> to == DialogueWorkStatus.IN_PROGRESS
                    || to == DialogueWorkStatus.WAITING_HUMAN
                    || to == DialogueWorkStatus.RESOLVED
                    || to == DialogueWorkStatus.CLOSED;
            case RESOLVED -> to == DialogueWorkStatus.IN_PROGRESS || to == DialogueWorkStatus.CLOSED;
            case CLOSED -> to == DialogueWorkStatus.IN_PROGRESS;
        };
    }

    private void markWorkItemChanged(UUID actorId, Instant now) {
        workItemUpdatedBy = actorId;
        workItemUpdatedAt = now;
        updatedAt = now;
    }

    private static int priorityRank(DialoguePriority value) {
        return switch (value) {
            case LOW -> 0;
            case NORMAL -> 1;
            case HIGH -> 2;
            case URGENT -> 3;
        };
    }
}
