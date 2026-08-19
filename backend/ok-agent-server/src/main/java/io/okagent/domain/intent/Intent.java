package io.okagent.domain.intent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A node in the customer-service intent tree. Intents form a self-referencing hierarchy
 * (parent_id) and carry purely semantic information (key, name, description, examples). The
 * binding between an intent and the sub-agent that handles it lives on the router agent's
 * {@code subagents_json} (each sub-agent declares which {@code intentKeys} it owns), not here.
 * The routing layer (IntentRouterService) flattens this tree and lets an LLM classifier pick
 * the best-matching node for an incoming query.
 */
@Entity
@Table(name = "intent")
public class Intent {

    @Id
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "intent_key", nullable = false, unique = true, length = 128)
    private String intentKey;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** JSON array of example user queries that map to this intent, e.g. ["我想贷款","怎么申请额度"]. */
    @Column(name = "examples_json", columnDefinition = "TEXT")
    private String examplesJson;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Intent() {}

    public Intent(UUID id, String intentKey, String name, UUID parentId) {
        this.id = id;
        this.intentKey = intentKey;
        this.name = name;
        this.parentId = parentId;
        this.examplesJson = "[]";
        this.sortOrder = 0;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void applyDefinition(String name, String description, String examplesJson, int sortOrder) {
        this.name = name;
        this.description = description == null ? "" : description;
        this.examplesJson = examplesJson == null || examplesJson.isBlank() ? "[]" : examplesJson;
        this.sortOrder = sortOrder;
        this.updatedAt = Instant.now();
    }

    public void moveUnder(UUID parentId) {
        this.parentId = parentId;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getIntentKey() {
        return intentKey;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getExamplesJson() {
        return examplesJson;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
