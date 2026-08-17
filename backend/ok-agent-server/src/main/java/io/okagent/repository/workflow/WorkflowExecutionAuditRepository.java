package io.okagent.repository.workflow;

import io.okagent.domain.workflow.WorkflowExecutionAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowExecutionAuditRepository extends JpaRepository<WorkflowExecutionAudit, UUID> {}
