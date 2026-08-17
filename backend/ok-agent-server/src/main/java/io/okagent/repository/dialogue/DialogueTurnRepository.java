package io.okagent.repository.dialogue;

import io.okagent.domain.dialogue.DialogueTurn;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DialogueTurnRepository extends JpaRepository<DialogueTurn, Long> {

    List<DialogueTurn> findBySessionIdOrderBySeqAsc(String sessionId);

    long countBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);
}
