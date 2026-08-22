package io.okagent.repository.channel;

import io.okagent.domain.channel.ChannelIlinkSession;
import io.okagent.domain.channel.IlinkLoginStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelIlinkSessionRepository extends JpaRepository<ChannelIlinkSession, UUID> {

    Optional<ChannelIlinkSession> findByChannelId(UUID channelId);

    /**
     * Pessimistic read of the session row. Serializes concurrent startLogin() calls (e.g. the QR
     * panel auto-start firing twice in React StrictMode) — each transaction takes an exclusive row
     * lock up front, so a following update never has to upgrade S→X and deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChannelIlinkSession s where s.channelId = :channelId")
    Optional<ChannelIlinkSession> findByChannelIdForUpdate(@Param("channelId") UUID channelId);

    /**
     * Idempotently ensures a session row exists. Uses {@code INSERT ... ON DUPLICATE KEY UPDATE}
     * (a no-op update on conflict) rather than {@code INSERT IGNORE}. The latter takes a shared
     * lock on the duplicate row which, combined with the subsequent UPDATE, causes a deadlock when
     * two transactions race. ON DUPLICATE KEY UPDATE takes an exclusive lock instead.
     */
    @Modifying
    @Query(value = "INSERT INTO channel_ilink_session "
            + "(channel_id, login_status, poll_cursor, created_at, updated_at) "
            + "VALUES (:channelId, 'LOGGED_OUT', '', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) "
            + "ON DUPLICATE KEY UPDATE channel_id = channel_id",
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
