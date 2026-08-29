package io.okagent.module.mcp.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mcp_tool_snapshot")
public class McpToolSnapshot {
    @Id
    private UUID id;

    @Column(name = "mcp_server_id", nullable = false)
    private UUID serverId;

    @Column(name = "tool_name", nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "input_schema_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String inputSchemaJson;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    protected McpToolSnapshot() {}

    public McpToolSnapshot(UUID serverId, String name, String description, String inputSchemaJson) {
        this.id = UUID.randomUUID();
        this.serverId = serverId;
        this.name = name;
        this.description = description == null ? "" : description;
        this.inputSchemaJson = inputSchemaJson;
        this.discoveredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getServerId() {
        return serverId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getInputSchemaJson() {
        return inputSchemaJson;
    }

    public Instant getDiscoveredAt() {
        return discoveredAt;
    }
}
