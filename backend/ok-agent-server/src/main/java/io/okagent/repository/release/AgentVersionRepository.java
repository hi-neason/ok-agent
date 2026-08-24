package io.okagent.repository.release;

import io.okagent.domain.release.AgentVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentVersionRepository extends JpaRepository<AgentVersion, UUID> {
    List<AgentVersion> findByAgentIdOrderByVersionNoDesc(UUID agentId);

    Optional<AgentVersion> findByAgentIdAndVersionNo(UUID agentId, int versionNo);

    Optional<AgentVersion> findTopByAgentIdOrderByVersionNoDesc(UUID agentId);

    boolean existsByAgentIdAndVersionNo(UUID agentId, int versionNo);

    boolean existsByContentHash(String contentHash);
}
