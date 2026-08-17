package io.okagent.infrastructure.store;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.transcript.TranscriptRef;
import io.agentscope.harness.agent.transcript.TranscriptStore;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * MySQL-backed {@link TranscriptStore}. Replaces the default filesystem transcript store so session
 * transcripts (immutable JSONL segments) are persisted in the same database as the rest of the
 * platform. Each segment is one row addressed by an auto-increment id; {@link #appendSegment} returns
 * that id as the storage key.
 */
@Repository
public class JdbcTranscriptStore implements TranscriptStore {

    private final JdbcTemplate jdbc;

    public JdbcTranscriptStore(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public String appendSegment(
            TranscriptRef ref, long seqStart, long seqEnd, String writerId, byte[] jsonl) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO agent_transcript (tenant, agent_id, session_id, seq_start, seq_end,"
                            + " writer_id, payload) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    new String[] {"id"});
            ps.setString(1, ref.tenant());
            ps.setString(2, ref.agentId());
            ps.setString(3, ref.sessionId());
            ps.setLong(4, seqStart);
            ps.setLong(5, seqEnd);
            ps.setString(6, writerId);
            ps.setBytes(7, jsonl);
            return ps;
        }, key);
        return String.valueOf(key.getKey().longValue());
    }

    @Override
    public List<TranscriptStore.SegmentInfo> listSegments(TranscriptRef ref) {
        return jdbc.query(
                "SELECT id, seq_start, seq_end, writer_id, created_at FROM agent_transcript "
                        + "WHERE tenant = ? AND agent_id = ? AND session_id = ? ORDER BY seq_start ASC",
                (rs, n) -> {
                    Timestamp ts = rs.getTimestamp("created_at");
                    return new TranscriptStore.SegmentInfo(
                            String.valueOf(rs.getLong("id")),
                            rs.getLong("seq_start"),
                            rs.getLong("seq_end"),
                            rs.getString("writer_id"),
                            ts == null ? Instant.EPOCH : ts.toInstant());
                },
                ref.tenant(), ref.agentId(), ref.sessionId());
    }

    @Override
    public InputStream readSegment(String segmentKey) {
        Long id = parseId(segmentKey);
        byte[] payload = jdbc.queryForObject(
                "SELECT payload FROM agent_transcript WHERE id = ?", (rs, n) -> rs.getBytes("payload"), id);
        if (payload == null) {
            payload = new byte[0];
        }
        return new ByteArrayInputStream(payload);
    }

    @Override
    public void delete(TranscriptRef ref) {
        jdbc.update(
                "DELETE FROM agent_transcript WHERE tenant = ? AND agent_id = ? AND session_id = ?",
                ref.tenant(), ref.agentId(), ref.sessionId());
    }

    /** Deletes every transcript segment for a session regardless of tenant/agent (used on reset). */
    public void deleteBySessionId(String sessionId) {
        jdbc.update("DELETE FROM agent_transcript WHERE session_id = ?", sessionId);
    }

    @Override
    public TranscriptStore withRuntimeContext(RuntimeContext rc) {
        return this;
    }

    private static Long parseId(String segmentKey) {
        try {
            return Long.parseLong(segmentKey);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unsupported transcript segment key: " + segmentKey);
        }
    }
}
