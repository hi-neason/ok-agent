package io.okagent.repository.observe;

import io.okagent.domain.observe.TraceSpan;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access for persisted execution-trace spans. */
public interface TraceSpanRepository extends JpaRepository<TraceSpan, Long> {

    /** Returns every span of one trace in insertion order (root first, then model/tool children). */
    List<TraceSpan> findByTraceIdOrderByIdAsc(String traceId);

    /** Deletes all spans of the given traces (used when purging/dropping a session). */
    void deleteByTraceIdIn(List<String> traceIds);

    /** Counts spans for an agent, used by observability summaries. */
    long countByAgentId(UUID agentId);

    /** Bulk-deletes trace spans older than the configured observability retention window. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TraceSpan t where t.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
