package io.okagent.module.persona.infrastructure.persistence;

import io.okagent.module.persona.domain.UserPersona;
import io.okagent.module.persona.domain.UserPersonaId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPersonaRepository extends JpaRepository<UserPersona, UserPersonaId> {

    Optional<UserPersona> findByIdUserIdAndIdAgentId(String userId, UUID agentId);

    /** All persona rows for a user across agents (used for GLOBAL injection merge). */
    List<UserPersona> findByIdUserId(String userId);

    /** Lightweight coverage rows for the management list (avoids loading LONGTEXT fields). */
    @Query("select p.id.userId as userId, p.id.agentId as agentId, p.summary as summary, "
            + "p.updatedAt as updatedAt from UserPersona p")
    List<PersonaCoverageRow> findCoverage();

    interface PersonaCoverageRow {
        String getUserId();

        UUID getAgentId();

        String getSummary();

        Instant getUpdatedAt();
    }
}
