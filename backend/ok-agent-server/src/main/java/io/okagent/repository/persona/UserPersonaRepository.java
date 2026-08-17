package io.okagent.repository.persona;

import io.okagent.domain.persona.UserPersona;
import io.okagent.domain.persona.UserPersonaId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPersonaRepository extends JpaRepository<UserPersona, UserPersonaId> {

    Optional<UserPersona> findByIdUserIdAndIdAgentId(String userId, java.util.UUID agentId);

    /** All persona rows for a user across agents (used for GLOBAL injection merge). */
    List<UserPersona> findByIdUserId(String userId);
}
