package io.okagent.repository.workflow;

import io.okagent.domain.workflow.WorkflowCatalogItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowCatalogItemRepository extends JpaRepository<WorkflowCatalogItem, UUID> {
    List<WorkflowCatalogItem> findBySourceId(UUID sourceId);
    Optional<WorkflowCatalogItem> findBySourceIdAndRemoteWorkflowId(UUID sourceId, String remoteWorkflowId);
    void deleteBySourceId(UUID sourceId);
    List<WorkflowCatalogItem> findAllByIdIn(Collection<UUID> ids);
}
