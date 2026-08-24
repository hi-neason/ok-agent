package io.okagent.repository.channel;

import io.okagent.domain.channel.ChannelAsset;
import io.okagent.domain.channel.ChannelRuntimeStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelAssetRepository extends JpaRepository<ChannelAsset, UUID> {
    List<ChannelAsset> findByBoundAgentId(UUID boundAgentId);

    List<ChannelAsset> findByEnabledTrue();

    /**
     * Directly sets runtime status/error, bypassing the persistence context's first-level cache.
     * Must run inside a transaction; used after a channel (re)starts outside the request
     * transaction, where a stale managed entity could otherwise mask the update.
     */
    @Modifying
    @Query("update ChannelAsset c set c.runtimeStatus = :status, c.lastError = :error, "
            + "c.updatedAt = :now where c.id = :id")
    int updateRuntimeStatus(
            @Param("id") UUID id,
            @Param("status") ChannelRuntimeStatus status,
            @Param("error") String error,
            @Param("now") Instant now);

    /**
     * Deletes a channel by id without an optimistic-lock version predicate. Returns the number of
     * rows removed (0 if it was already gone). This makes concurrent/duplicate deletes idempotent
     * instead of throwing {@code StaleStateException} / {@code ObjectOptimisticLockingFailureException}.
     */
    @Modifying
    @Query("delete from ChannelAsset c where c.id = :id")
    int deleteChannelById(@Param("id") UUID id);
}
