package io.okagent.repository.observe;

import io.okagent.domain.observe.TraceSpan;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access for persisted execution-trace spans. */
public interface TraceSpanRepository extends JpaRepository<TraceSpan, Long> {

    /** Returns every span of one trace in insertion order (root first, then model/tool children). */
    List<TraceSpan> findByTraceIdOrderByIdAsc(String traceId);

    /** Deletes all spans of the given traces (used when purging/dropping a session). */
    void deleteByTraceIdIn(List<String> traceIds);

    /** Counts spans for an agent, used by observability summaries. */
    long countByAgentId(UUID agentId);
}
