package io.okagent.repository.agent;

import io.okagent.domain.agent.AgentAsset;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentAssetRepository extends JpaRepository<AgentAsset, UUID> {
    boolean existsByAgentKey(String agentKey);
}
