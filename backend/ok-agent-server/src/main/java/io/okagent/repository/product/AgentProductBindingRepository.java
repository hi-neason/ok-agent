package io.okagent.repository.product;

import io.okagent.domain.product.AgentProductBinding;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProductBindingRepository extends JpaRepository<AgentProductBinding, UUID> {
    Optional<AgentProductBinding> findByAgentId(UUID agentId);

    void deleteByAgentId(UUID agentId);
}
