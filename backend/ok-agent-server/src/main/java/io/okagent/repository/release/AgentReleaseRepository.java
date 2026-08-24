package io.okagent.repository.release;

import io.okagent.domain.release.AgentRelease;
import io.okagent.domain.release.ReleaseStatus;
import io.okagent.domain.release.ReleaseTargetType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentReleaseRepository extends JpaRepository<AgentRelease, UUID> {
    Optional<AgentRelease> findByTargetTypeAndTargetIdAndStatus(
            ReleaseTargetType targetType, UUID targetId, ReleaseStatus status);

    List<AgentRelease> findByTargetTypeAndTargetIdOrderByPublishedAtDesc(
            ReleaseTargetType targetType, UUID targetId);

    List<AgentRelease> findByAgentIdOrderByPublishedAtDesc(UUID agentId);
}
