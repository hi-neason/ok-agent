package io.okagent.web.workflow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowCatalogItemResponse(
        UUID id,
        UUID sourceId,
        String sourceName,
        String remoteWorkflowId,
        String name,
        String remoteMode,
        boolean active,
        List<String> tags,
        String remoteDescription,
        String description,
        String inputSchemaJson,
        String metadataStatus,
        Instant updatedAt) {}
