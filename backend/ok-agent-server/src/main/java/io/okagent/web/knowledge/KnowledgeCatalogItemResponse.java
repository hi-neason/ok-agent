package io.okagent.web.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KnowledgeCatalogItemResponse(
        UUID id,
        UUID sourceId,
        String sourceName,
        String remoteKnowledgeId,
        String name,
        boolean active,
        List<String> tags,
        String remoteDescription,
        String description,
        int documentCount,
        long wordCount,
        String metadataStatus,
        Instant updatedAt) {}
