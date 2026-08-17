package io.okagent.service.observe;

import io.okagent.domain.observe.TraceSpan;
import java.util.List;

/**
 * Receives a completed turn's spans after the agent event stream finishes.
 *
 * <p>Implementations persist the spans without blocking the agent reply (typically asynchronously).
 * Sinks are invoked at most once per turn from {@code doFinally}.
 */
public interface TraceSink {

    /**
     * Persists the spans of one finished turn.
     *
     * @param spans ordered spans (root first); never empty when a trace was captured
     */
    void saveAll(List<TraceSpan> spans);
}
