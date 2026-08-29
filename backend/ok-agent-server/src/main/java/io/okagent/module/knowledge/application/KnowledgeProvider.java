package io.okagent.module.knowledge.application;

import java.util.List;

/**
 * Service-provider interface for external knowledge-base systems. An implementation knows how to
 * authenticate against one system type (Dify, RAGFlow, a vector DB, ...), enumerate the knowledge
 * bases it exposes, and retrieve relevant chunks. New systems are added by implementing this
 * interface and registering a Spring component; the control plane routes by {@link #type()}.
 */
public interface KnowledgeProvider {

    /** Source type identifier, matching {@code KnowledgeSourceType}, e.g. {@code DIFY}. */
    String type();

    /** Tests connectivity/credentials without persisting anything. */
    ConnectionTestResult test(KnowledgeSourceConfig config);

    /** Lists the knowledge bases reachable under the given source credentials. */
    List<RemoteKnowledgeSummary> listKnowledgeBases(KnowledgeSourceConfig config);

    /**
     * Retrieves relevant chunks for a query.
     *
     * @param remoteKnowledgeId the remote knowledge base/dataset id
     * @param query the natural-language query
     * @param topK maximum number of chunks to return (null lets the provider use its default)
     * @param scoreThreshold optional relevance threshold (null = provider default)
     * @param endUserId the terminal user id for audit/quota passthrough (may be null)
     */
    List<RetrievedChunk> retrieve(
            KnowledgeSourceConfig config,
            String remoteKnowledgeId,
            String query,
            Integer topK,
            Double scoreThreshold,
            String endUserId);
}
