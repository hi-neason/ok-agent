package io.okagent.domain.persona;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Per-user persona record. Structured insight (tags / preferences / facts / summary) lives in this
 * table; the free-form long-term memory (MEMORY.md) is persisted separately via {@code JdbcBaseStore}
 * under the {@code users/{userId}/persona} namespace. The two together form the user dimension of
 * memory, distinct from the agent's own memory.
 */
@Entity
@Table(name = "user_persona")
public class UserPersona {

    @Id
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

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

    public UserPersona(String userId) {
        this.userId = userId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getUserId() {
        return userId;
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
