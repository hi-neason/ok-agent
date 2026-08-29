package io.okagent.module.persona.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite primary key for a persona record: one row per (user, agent). */
@Embeddable
public class UserPersonaId implements Serializable {

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    protected UserPersonaId() {}

    public UserPersonaId(String userId, UUID agentId) {
        this.userId = userId;
        this.agentId = agentId;
    }

    public String getUserId() {
        return userId;
    }

    public UUID getAgentId() {
        return agentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserPersonaId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(agentId, that.agentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, agentId);
    }
}
