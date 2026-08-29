package io.okagent.module.workflow.infrastructure.persistence;

import io.okagent.module.workflow.domain.WorkflowExecutionAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowExecutionAuditRepository extends JpaRepository<WorkflowExecutionAudit, UUID> {}
