package io.okagent.repository.workflow;

import io.okagent.domain.workflow.AgentWorkflowBinding;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentWorkflowBindingRepository extends JpaRepository<AgentWorkflowBinding, UUID> {
    List<AgentWorkflowBinding> findByAgentId(UUID agentId);
    List<AgentWorkflowBinding> findByAgentIdIn(Collection<UUID> agentIds);
    List<AgentWorkflowBinding> findByCatalogItemId(UUID catalogItemId);
    void deleteByAgentId(UUID agentId);
}
