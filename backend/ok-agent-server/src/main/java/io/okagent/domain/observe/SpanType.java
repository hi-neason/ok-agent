package io.okagent.domain.observe;

/** The kind of operation a {@link TraceSpan} records. Mirrors the gen_ai.operation.name convention. */
public enum SpanType {
    /** Wraps the entire agent reply (one turn). Root of the trace. */
    AGENT,
    /** One model / LLM API call inside the ReAct loop. */
    MODEL,
    /** One tool execution (MCP, knowledge base, workflow, or built-in tool). */
    TOOL
}
