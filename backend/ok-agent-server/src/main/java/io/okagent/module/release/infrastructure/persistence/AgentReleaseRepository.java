package io.okagent.module.release.infrastructure.persistence;

import io.okagent.module.release.domain.AgentRelease;
import io.okagent.module.release.domain.ReleaseStatus;
import io.okagent.module.release.domain.ReleaseTargetType;
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
