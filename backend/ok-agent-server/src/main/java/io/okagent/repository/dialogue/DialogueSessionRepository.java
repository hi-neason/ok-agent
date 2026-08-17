package io.okagent.repository.dialogue;

import io.okagent.domain.dialogue.DialogueSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DialogueSessionRepository
        extends JpaRepository<DialogueSession, String>, JpaSpecificationExecutor<DialogueSession> {

    boolean existsBySessionId(String sessionId);

    java.util.List<DialogueSession> findByAgentIdOrderByUpdatedAtDesc(UUID agentId);
}
