package io.okagent.module.agent.infrastructure.persistence;

import io.okagent.module.agent.domain.AgentAsset;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentAssetRepository extends JpaRepository<AgentAsset, UUID> {
    boolean existsByAgentKey(String agentKey);
}
