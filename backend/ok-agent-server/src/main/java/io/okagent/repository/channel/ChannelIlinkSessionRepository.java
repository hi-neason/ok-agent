package io.okagent.repository.channel;

import io.okagent.domain.channel.ChannelIlinkSession;
import io.okagent.domain.channel.IlinkLoginStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelIlinkSessionRepository extends JpaRepository<ChannelIlinkSession, UUID> {

    Optional<ChannelIlinkSession> findByChannelId(UUID channelId);

    /**
     * Idempotently ensures a session row exists for the given channel. Race-safe under concurrent
     * calls (e.g. the QR panel auto-start firing twice) thanks to MySQL {@code INSERT IGNORE}: the
     * second concurrent insert silently no-ops instead of throwing a duplicate-key error.
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO channel_ilink_session "
            + "(channel_id, login_status, poll_cursor, created_at, updated_at) "
            + "VALUES (:channelId, 'LOGGED_OUT', '', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
            nativeQuery = true)
    int insertIfAbsent(@Param("channelId") UUID channelId);

    /** Persists the advanced long-polling cursor without touching unrelated columns. */
    @Modifying
    @Query("update ChannelIlinkSession s set s.pollCursor = :cursor, s.updatedAt = CURRENT_TIMESTAMP "
            + "where s.channelId = :channelId")
    int updateCursor(@Param("channelId") UUID channelId, @Param("cursor") String cursor);

    @Modifying
    @Query("update ChannelIlinkSession s set s.loginStatus = :status, s.lastError = :error, "
            + "s.updatedAt = CURRENT_TIMESTAMP where s.channelId = :channelId")
    int updateLoginStatus(
            @Param("channelId") UUID channelId,
            @Param("status") IlinkLoginStatus status,
            @Param("error") String error);
}
