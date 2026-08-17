package io.okagent.domain.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A single discoverable workflow within a source, together with its platform-curated metadata.
 * For Dify one source maps to one app/workflow; for instance-scoped systems (n8n) one source may
 * yield many. The input schema and description here are the single global source of truth for
 * runtime tooling; agent bindings may override the description but never the schema.
 */
@Entity
@Table(name = "workflow_catalog_item")
public class WorkflowCatalogItem {
    @Id
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "remote_workflow_id", nullable = false, length = 255)
    private String remoteWorkflowId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "remote_mode", nullable = false, length = 32)
    private String remoteMode;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "tags_json", nullable = false, columnDefinition = "TEXT")
    private String tagsJson;

    @Column(name = "remote_description", nullable = false, columnDefinition = "TEXT")
    private String remoteDescription;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "input_schema_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String inputSchemaJson;

    @Column(name = "remote_raw_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String remoteRawJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "metadata_status", nullable = false, length = 32)
    private WorkflowMetadataStatus metadataStatus;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkflowCatalogItem() {}

    public WorkflowCatalogItem(
            UUID id,
            UUID sourceId,
            String remoteWorkflowId,
            String name,
            String remoteMode,
            boolean active,
            String tagsJson,
            String remoteDescription,
            String description,
            String inputSchemaJson,
            String remoteRawJson,
            WorkflowMetadataStatus metadataStatus) {
        this.id = id;
        this.sourceId = sourceId;
        this.remoteWorkflowId = remoteWorkflowId;
        this.name = name;
        this.remoteMode = remoteMode;
        this.active = active;
        this.tagsJson = tagsJson;
        this.remoteDescription = remoteDescription;
        this.description = description;
        this.inputSchemaJson = inputSchemaJson;
        this.remoteRawJson = remoteRawJson;
        this.metadataStatus = metadataStatus;
        this.discoveredAt = Instant.now();
        this.updatedAt = discoveredAt;
    }

    /** Applies freshly discovered remote data while preserving any owner-curated description. */
    public void applyRemoteUpdate(
            String name,
            String remoteMode,
            boolean active,
            String tagsJson,
            String remoteDescription,
            String inputSchemaJson,
            String remoteRawJson) {
        this.name = name;
        this.remoteMode = remoteMode;
        this.active = active;
        this.tagsJson = tagsJson;
        this.remoteDescription = remoteDescription;
        this.inputSchemaJson = inputSchemaJson;
        this.remoteRawJson = remoteRawJson;
        this.updatedAt = Instant.now();
    }

    public void updateMetadata(String description, WorkflowMetadataStatus status) {
        this.description = description == null ? "" : description;
        this.metadataStatus = status;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getSourceId() { return sourceId; }
    public String getRemoteWorkflowId() { return remoteWorkflowId; }
    public String getName() { return name; }
    public String getRemoteMode() { return remoteMode; }
    public boolean isActive() { return active; }
    public String getTagsJson() { return tagsJson; }
    public String getRemoteDescription() { return remoteDescription; }
    public String getDescription() { return description; }
    public String getInputSchemaJson() { return inputSchemaJson; }
    public String getRemoteRawJson() { return remoteRawJson; }
    public WorkflowMetadataStatus getMetadataStatus() { return metadataStatus; }
    public Instant getDiscoveredAt() { return discoveredAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
