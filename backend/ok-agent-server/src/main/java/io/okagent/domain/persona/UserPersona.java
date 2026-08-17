package io.okagent.domain.persona;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-(user, agent) persona record. Each agent independently extracts and stores its own view of a
 * user. The structured insight (tags / preferences / facts / summary) lives in this table; the
 * free-form long-term memory (MEMORY.md) is persisted separately via {@code JdbcBaseStore} under the
 * {@code users/{userId}/persona/{agentId}} namespace.
 *
 * <p>The per-agent split lets injection strategies choose between this agent's own persona
 * (SELF_ONLY) or a merge across all agents (GLOBAL).
 */
@Entity
@Table(name = "user_persona")
public class UserPersona {

    @EmbeddedId
    private UserPersonaId id;

    @Column(name = "tags_json", columnDefinition = "LONGTEXT")
    private String tagsJson;

    @Column(name = "preferences_json", columnDefinition = "LONGTEXT")
    private String preferencesJson;

    @Column(name = "facts", columnDefinition = "LONGTEXT")
    private String facts;

    @Column(name = "summary", columnDefinition = "LONGTEXT")
    private String summary;

    @Column(name = "last_extracted_at")
    private Instant lastExtractedAt;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserPersona() {}

    public UserPersona(String userId, UUID agentId) {
        this.id = new UserPersonaId(userId, agentId);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UserPersonaId getId() {
        return id;
    }

    public String getUserId() {
        return id.getUserId();
    }

    public UUID getAgentId() {
        return id.getAgentId();
    }

    public String getTagsJson() {
        return tagsJson;
    }

    public void setTagsJson(String tagsJson) {
        this.tagsJson = tagsJson;
    }

    public String getPreferencesJson() {
        return preferencesJson;
    }

    public void setPreferencesJson(String preferencesJson) {
        this.preferencesJson = preferencesJson;
    }

    public String getFacts() {
        return facts;
    }

    public void setFacts(String facts) {
        this.facts = facts;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Instant getLastExtractedAt() {
        return lastExtractedAt;
    }

    public void setLastExtractedAt(Instant lastExtractedAt) {
        this.lastExtractedAt = lastExtractedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
