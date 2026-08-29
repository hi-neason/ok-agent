package io.okagent.module.workflow.infrastructure.persistence;

import io.okagent.module.workflow.domain.WorkflowSource;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowSourceRepository extends JpaRepository<WorkflowSource, UUID> {
    boolean existsBySourceKey(String sourceKey);

    Optional<WorkflowSource> findBySourceKey(String sourceKey);
}
