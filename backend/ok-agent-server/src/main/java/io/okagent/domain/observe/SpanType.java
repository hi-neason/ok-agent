package io.okagent.domain.observe;

/**
 * The kind of operation a {@link TraceSpan} records.
 *
 * <p>AGENT/MODEL mirror the gen_ai.operation.name convention. The former TOOL bucket is split by
 * where the tool comes from so the waterfall makes external integrations distinguishable at a
 * glance:
 *
 * <ul>
 *   <li>{@link #MCP} — tools served by a connected MCP server ({@code McpTool}).</li>
 *   <li>{@link #SKILL} — harness skill tools (load_skill_through_path, propose_skill, ...).</li>
 *   <li>{@link #WORKFLOW} — ok-agent external workflow tools (list/describe/start_workflow).</li>
 *   <li>{@link #RAG} — ok-agent knowledge-base retrieval tools (list/search_knowledge).</li>
 *   <li>{@link #TOOL} — harness built-in tools: file/shell/web/task/memory, and anything else
 *       not classified above.</li>
 * </ul>
 */
public enum SpanType {
    /** Wraps the entire agent reply (one turn). Root of the trace. */
    AGENT,
    /** One model / LLM API call inside the ReAct loop. */
    MODEL,
    /** A tool served by a connected MCP server. */
    MCP,
    /** A harness skill tool. */
    SKILL,
    /** An external workflow invocation (ok-agent workflow integration). */
    WORKFLOW,
    /** A knowledge-base / RAG retrieval (ok-agent knowledge integration). */
    RAG,
    /** A harness built-in tool (file/shell/web/task/...) or any otherwise-unclassified tool. */
    TOOL
}
