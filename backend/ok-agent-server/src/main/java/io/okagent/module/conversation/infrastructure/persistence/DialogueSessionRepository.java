package io.okagent.module.conversation.infrastructure.persistence;

import io.okagent.module.conversation.domain.DialogueSession;
import io.okagent.module.conversation.domain.DialogueWorkStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DialogueSessionRepository
        extends JpaRepository<DialogueSession, String>, JpaSpecificationExecutor<DialogueSession> {
    @Query(value = "select case when s.userId is null or s.userId = '' then concat('anonymous:', s.sessionId) else concat('user:', s.userId) end from DialogueSession s "
            + "where (:status is null or s.workStatus = :status) group by case when s.userId is null or s.userId = '' then concat('anonymous:', s.sessionId) else concat('user:', s.userId) end "
            + "order by max(s.updatedAt) desc, case when s.userId is null or s.userId = '' then concat('anonymous:', s.sessionId) else concat('user:', s.userId) end asc",
            countQuery = "select count(distinct case when s.userId is null or s.userId = '' then concat('anonymous:', s.sessionId) else concat('user:', s.userId) end) from DialogueSession s "
            + "where (:status is null or s.workStatus = :status)")
    org.springframework.data.domain.Page<String> customerKeys(@Param("status") DialogueWorkStatus status, org.springframework.data.domain.Pageable pageable);

    @Query("select s from DialogueSession s where (case when s.userId is null or s.userId = '' then concat('anonymous:', s.sessionId) else concat('user:', s.userId) end) in :keys "
            + "and (:status is null or s.workStatus = :status) order by s.updatedAt desc, s.sessionId asc")
    java.util.List<DialogueSession> customerSessions(@Param("keys") java.util.List<String> keys, @Param("status") DialogueWorkStatus status);

    long countByWorkStatus(DialogueWorkStatus status);

    boolean existsBySessionId(String sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DialogueSession s where s.sessionId = :sessionId")
    Optional<DialogueSession> findForTurnAllocation(@Param("sessionId") String sessionId);

    java.util.List<DialogueSession> findByAgentIdOrderByUpdatedAtDesc(UUID agentId);
}
