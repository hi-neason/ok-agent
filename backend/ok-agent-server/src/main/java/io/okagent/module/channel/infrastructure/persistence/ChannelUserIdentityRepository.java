package io.okagent.module.channel.infrastructure.persistence;

import io.okagent.module.channel.domain.ChannelUserIdentity;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC-backed access for {@link ChannelUserIdentity}. Upsert uses {@code INSERT ... ON DUPLICATE
 * KEY UPDATE} so concurrent inbound messages from the same sender are recorded atomically without
 * optimistic-lock contention. The primary key is generated in MySQL via {@code UUID_TO_BIN(UUID())}.
 */
@Repository
public class ChannelUserIdentityRepository {

    private final JdbcTemplate jdbc;

    public ChannelUserIdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<ChannelUserIdentity> ROW_MAPPER = (rs, i) -> {
        return new ChannelUserIdentity(
                rs.getObject("id", UUID.class),
                rs.getObject("linked_user_id", UUID.class),
                rs.getString("channel_type"),
                rs.getString("channel_key"),
                rs.getString("external_id"),
                rs.getString("union_id"),
                rs.getString("tenant_key"),
                rs.getString("display_name"),
                rs.getString("avatar_url"),
                toInstant(rs.getTimestamp("first_seen_at")),
                toInstant(rs.getTimestamp("last_seen_at")),
                toInstant(rs.getTimestamp("last_message_at")),
                rs.getLong("message_count"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    };

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static final String BASE_SELECT =
            """
            SELECT id, linked_user_id, channel_type, channel_key, external_id, union_id, tenant_key,
                   display_name, avatar_url, first_seen_at, last_seen_at, last_message_at,
                   message_count, created_at, updated_at
            FROM channel_user_identity
            """;

    private static final String UPSERT_SQL =
            """
            INSERT INTO channel_user_identity
              (id, channel_type, channel_key, external_id, union_id, tenant_key,
               display_name, avatar_url, first_seen_at, last_seen_at, last_message_at,
               message_count, version, created_at, updated_at, updated_by)
            VALUES (UUID_TO_BIN(UUID()), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0, ?, ?, 'system')
            ON DUPLICATE KEY UPDATE
              last_seen_at = VALUES(last_seen_at),
              last_message_at = VALUES(last_message_at),
              message_count = message_count + 1,
              union_id = COALESCE(NULLIF(VALUES(union_id), ''), union_id),
              tenant_key = COALESCE(NULLIF(VALUES(tenant_key), ''), tenant_key),
              display_name = COALESCE(NULLIF(VALUES(display_name), ''), display_name),
              avatar_url = COALESCE(NULLIF(VALUES(avatar_url), ''), avatar_url),
              updated_at = VALUES(updated_at)
            """;

    /** Atomically inserts a new identity or updates an existing one with a new message touch. */
    public void upsertTouch(
            String channelType,
            String channelKey,
            String externalId,
            String unionId,
            String tenantKey,
            String displayName,
            String avatarUrl) {
        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);
        jdbc.update(
                UPSERT_SQL,
                channelType,
                channelKey,
                externalId,
                emptyToNull(unionId),
                emptyToNull(tenantKey),
                emptyToNull(displayName),
                emptyToNull(avatarUrl),
                ts,
                ts,
                ts,
                ts,
                ts);
    }

    public Optional<ChannelUserIdentity> find(String channelType, String channelKey, String externalId) {
        List<ChannelUserIdentity> results = jdbc.query(
                BASE_SELECT + " WHERE channel_type = ? AND channel_key = ? AND external_id = ?",
                ROW_MAPPER,
                channelType,
                channelKey,
                externalId);
        return results.stream().findFirst();
    }

    /** Lists identities, most recently seen first. Supports optional channelType/channelKey filters. */
    public List<ChannelUserIdentity> list(String channelType, String channelKey, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        if (channelType != null && channelKey != null) {
            return jdbc.query(
                    BASE_SELECT + " WHERE channel_type = ? AND channel_key = ? ORDER BY last_seen_at DESC LIMIT ?",
                    ROW_MAPPER,
                    channelType,
                    channelKey,
                    safeLimit);
        }
        if (channelType != null) {
            return jdbc.query(
                    BASE_SELECT + " WHERE channel_type = ? ORDER BY last_seen_at DESC LIMIT ?",
                    ROW_MAPPER,
                    channelType,
                    safeLimit);
        }
        return jdbc.query(BASE_SELECT + " ORDER BY last_seen_at DESC LIMIT ?", ROW_MAPPER, safeLimit);
    }

    /** Counts distinct identities seen on a given channel instance. */
    public long countByChannel(String channelKey) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM channel_user_identity WHERE channel_key = ?", Long.class, channelKey);
        return count == null ? 0 : count;
    }

    /** Counts provider identities aggregated under a given one-user-id. */
    public long countByLinkedUserId(UUID userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM channel_user_identity WHERE linked_user_id = UUID_TO_BIN(?)",
                Long.class,
                userId.toString());
        return count == null ? 0 : count;
    }

    /** Sets the one-user-id principal an identity aggregates under. */
    public void linkUser(UUID identityId, UUID userId) {
        jdbc.update(
                "UPDATE channel_user_identity SET linked_user_id = UUID_TO_BIN(?), updated_at = NOW(6) "
                        + "WHERE id = UUID_TO_BIN(?)",
                userId.toString(),
                identityId.toString());
    }

    /** Lists all provider identities aggregated under a given one-user-id. */
    public List<ChannelUserIdentity> findByLinkedUserId(UUID userId) {
        return jdbc.query(
                BASE_SELECT + " WHERE linked_user_id = UUID_TO_BIN(?) ORDER BY last_seen_at DESC",
                ROW_MAPPER,
                userId.toString());
    }

    /**
     * Reassigns all identities linked to {@code fromUserId} over to {@code toUserId}. Used when
     * merging two one-user-id principals.
     */
    public int reassignLinkedUser(UUID fromUserId, UUID toUserId) {
        return jdbc.update(
                "UPDATE channel_user_identity SET linked_user_id = UUID_TO_BIN(?), updated_at = NOW(6) "
                        + "WHERE linked_user_id = UUID_TO_BIN(?)",
                toUserId.toString(),
                fromUserId.toString());
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
