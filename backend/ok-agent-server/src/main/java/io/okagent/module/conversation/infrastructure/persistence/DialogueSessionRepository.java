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
    long countByWorkStatus(DialogueWorkStatus status);

    boolean existsBySessionId(String sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DialogueSession s where s.sessionId = :sessionId")
    Optional<DialogueSession> findForTurnAllocation(@Param("sessionId") String sessionId);

    java.util.List<DialogueSession> findByAgentIdOrderByUpdatedAtDesc(UUID agentId);
}
