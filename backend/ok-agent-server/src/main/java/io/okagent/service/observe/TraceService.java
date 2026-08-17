package io.okagent.service.observe;

import io.okagent.domain.observe.TraceSpan;
import java.util.List;
import java.util.Optional;

/**
 * Read/write access for execution traces. Writes are asynchronous and best-effort (a failed
 * persistence must never affect the agent reply); reads power the observability trace view.
 */
public interface TraceService {

    /**
     * Persists the spans of a finished turn. Implementations return immediately and write
     * off-thread.
     */
    void saveAll(List<TraceSpan> spans);

    /** Loads the full ordered span list of one trace, for the tree/span detail view. */
    Optional<List<TraceSpan>> findTrace(String traceId);
}
