package io.okagent.repository.knowledge;

import io.okagent.domain.knowledge.AgentKnowledgeBinding;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentKnowledgeBindingRepository extends JpaRepository<AgentKnowledgeBinding, UUID> {
    List<AgentKnowledgeBinding> findByAgentId(UUID agentId);

    List<AgentKnowledgeBinding> findByAgentIdIn(Collection<UUID> agentIds);

    List<AgentKnowledgeBinding> findByCatalogItemId(UUID catalogItemId);

    void deleteByAgentId(UUID agentId);
}
