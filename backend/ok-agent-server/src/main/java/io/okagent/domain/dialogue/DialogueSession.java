package io.okagent.domain.dialogue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    public DialogueSession() {}

    public DialogueSession(String sessionId, UUID agentId, String title, String userId, Instant createdAt) {
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.title = title;
        this.userId = userId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
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
}
