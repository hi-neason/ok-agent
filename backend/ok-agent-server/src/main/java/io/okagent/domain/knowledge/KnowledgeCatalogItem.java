package io.okagent.domain.knowledge;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A single discoverable knowledge base within a source, with platform-curated metadata. For Dify one
 * dataset-level API key (one source) can yield many datasets. The owner-curated {@code description}
 * here is the single global source of truth telling the model when this knowledge base applies;
 * agent bindings may override the description but never the identity of the base.
 */
@Entity
@Table(name = "knowledge_catalog_item")
public class KnowledgeCatalogItem {
    @Id
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "remote_knowledge_id", nullable = false, length = 255)
    private String remoteKnowledgeId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "document_count", nullable = false)
    private int documentCount;

    @Column(name = "word_count", nullable = false)
    private long wordCount;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "tags_json", nullable = false, columnDefinition = "TEXT")
    private String tagsJson;

    @Column(name = "remote_description", nullable = false, columnDefinition = "TEXT")
    private String remoteDescription;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "remote_raw_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String remoteRawJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "metadata_status", nullable = false, length = 32)
    private KnowledgeMetadataStatus metadataStatus;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KnowledgeCatalogItem() {}

    public KnowledgeCatalogItem(
            UUID id,
            UUID sourceId,
            String remoteKnowledgeId,
            String name,
            int documentCount,
            long wordCount,
            boolean active,
            String tagsJson,
            String remoteDescription,
            String description,
            String remoteRawJson,
            KnowledgeMetadataStatus metadataStatus) {
        this.id = id;
        this.sourceId = sourceId;
        this.remoteKnowledgeId = remoteKnowledgeId;
        this.name = name;
        this.documentCount = documentCount;
        this.wordCount = wordCount;
        this.active = active;
        this.tagsJson = tagsJson;
        this.remoteDescription = remoteDescription;
        this.description = description;
        this.remoteRawJson = remoteRawJson;
        this.metadataStatus = metadataStatus;
        this.discoveredAt = Instant.now();
        this.updatedAt = discoveredAt;
    }

    /** Applies freshly discovered remote data while preserving any owner-curated description. */
    public void applyRemoteUpdate(
            String name,
            int documentCount,
            long wordCount,
            boolean active,
            String tagsJson,
            String remoteDescription,
            String remoteRawJson) {
        this.name = name;
        this.documentCount = documentCount;
        this.wordCount = wordCount;
        this.active = active;
        this.tagsJson = tagsJson;
        this.remoteDescription = remoteDescription;
        this.remoteRawJson = remoteRawJson;
        this.updatedAt = Instant.now();
    }

    public void updateMetadata(String description, KnowledgeMetadataStatus status) {
        this.description = description == null ? "" : description;
        this.metadataStatus = status;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getSourceId() { return sourceId; }
    public String getRemoteKnowledgeId() { return remoteKnowledgeId; }
    public String getName() { return name; }
    public int getDocumentCount() { return documentCount; }
    public long getWordCount() { return wordCount; }
    public boolean isActive() { return active; }
    public String getTagsJson() { return tagsJson; }
    public String getRemoteDescription() { return remoteDescription; }
    public String getDescription() { return description; }
    public String getRemoteRawJson() { return remoteRawJson; }
    public KnowledgeMetadataStatus getMetadataStatus() { return metadataStatus; }
    public Instant getDiscoveredAt() { return discoveredAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
