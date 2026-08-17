package io.okagent.service.knowledge;

import java.util.List;

/** A discoverable knowledge base within a source, as reported by the remote system. */
public record RemoteKnowledgeSummary(
        String remoteKnowledgeId,
        String name,
        boolean active,
        List<String> tags,
        String remoteDescription,
        int documentCount,
        long wordCount) {}
