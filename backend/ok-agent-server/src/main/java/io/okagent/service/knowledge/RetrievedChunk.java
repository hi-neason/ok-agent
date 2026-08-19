package io.okagent.service.knowledge;

/**
 * A single retrieved chunk from a knowledge base. {@code content} is the segment text;
 * {@code documentName} identifies the source document; {@code score} is the relevance score when the
 * provider supplies one.
 */
public record RetrievedChunk(String content, String documentName, String segmentId, Double score) {}
