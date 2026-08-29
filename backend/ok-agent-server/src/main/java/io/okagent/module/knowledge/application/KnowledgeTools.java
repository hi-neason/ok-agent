package io.okagent.module.knowledge.application;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The read-only tools exposed to the LLM for retrieving from external knowledge bases. One instance
 * is built per agent (holding its agentId), so the catalog authorizes every call against that
 * agent's bindings. This implements agentic RAG: the model searches on demand rather than having
 * chunks pre-injected into its context.
 */
public class KnowledgeTools {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeTools.class);

    private final KnowledgeRuntimeCatalog catalog;
    private final UUID agentId;

    public KnowledgeTools(KnowledgeRuntimeCatalog catalog, UUID agentId) {
        this.catalog = catalog;
        this.agentId = agentId;
    }

    @Tool(
            name = "list_knowledge_bases",
            readOnly = true,
            description = "List the external knowledge bases available to this agent. Each entry has an id,"
                    + " a name and a one-line description of what information it contains. Call"
                    + " this before search_knowledge to pick the most relevant knowledge base(s).")
    public String listKnowledgeBases(RuntimeContext ctx) {
        try {
            List<BoundKnowledge> bases = catalog.listForAgent(agentId);
            if (bases.isEmpty()) {
                return "No external knowledge bases are bound to this agent.";
            }
            var sb = new StringBuilder();
            for (var b : bases) {
                sb.append("- id: ")
                        .append(b.catalogItemId())
                        .append("\n  name: ")
                        .append(b.name())
                        .append("\n  source: ")
                        .append(b.sourceKey())
                        .append("\n  description: ")
                        .append(safe(b.description()))
                        .append('\n');
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("list_knowledge_bases failed for agent {}: {}", agentId, e.getMessage());
            return "Error listing knowledge bases: " + e.getMessage();
        }
    }

    @Tool(
            name = "search_knowledge",
            readOnly = true,
            description = "Search an external knowledge base and return relevant text chunks. Use this to"
                    + " ground answers in the bound knowledge bases when the user asks about"
                    + " information they contain. Pass the knowledgeBaseId from"
                    + " list_knowledge_bases and a concise natural-language query. Returns up to"
                    + " topK chunks with their source document names; cite them in your answer.")
    public String searchKnowledge(
            RuntimeContext ctx,
            @ToolParam(name = "knowledgeBaseId", description = "The catalog item id from list_knowledge_bases")
                    String knowledgeBaseId,
            @ToolParam(name = "query", description = "The natural-language search query") String query,
            @ToolParam(
                            name = "topK",
                            description = "Maximum number of chunks to return (1-50); omit for the binding default",
                            required = false)
                    Integer topK) {
        try {
            var itemId = parseId(knowledgeBaseId);
            String userId = ctx == null ? null : ctx.getUserId();
            String sessionId = ctx == null ? null : ctx.getSessionId();
            List<RetrievedChunk> chunks = catalog.retrieve(agentId, itemId, query, topK, null, userId, sessionId);
            if (chunks.isEmpty()) {
                return "No relevant chunks found in this knowledge base.";
            }
            var sb = new StringBuilder();
            for (int i = 0; i < chunks.size(); i++) {
                var c = chunks.get(i);
                sb.append("--- chunk ").append(i + 1);
                if (!c.documentName().isBlank())
                    sb.append(" (source: ").append(c.documentName()).append(')');
                if (c.score() != null)
                    sb.append(" [score: ")
                            .append(String.format("%.3f", c.score()))
                            .append(']');
                sb.append(" ---\n").append(c.content().trim()).append('\n');
            }
            return sb.toString().trim();
        } catch (SecurityException e) {
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            log.warn("search_knowledge failed for agent {}: {}", agentId, e.getMessage());
            return "Error searching knowledge: " + e.getMessage();
        }
    }

    private UUID parseId(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid knowledgeBaseId: " + value);
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
