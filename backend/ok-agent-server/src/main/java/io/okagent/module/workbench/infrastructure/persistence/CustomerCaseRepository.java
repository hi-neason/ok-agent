package io.okagent.module.workbench.infrastructure.persistence;

import io.okagent.module.workbench.domain.CustomerCase;
import io.okagent.module.workbench.domain.CustomerCaseType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerCaseRepository extends JpaRepository<CustomerCase, UUID> {
    long countByType(CustomerCaseType type);

    List<CustomerCase> findBySourceSessionIdOrderByCreatedAtAsc(String sourceSessionId);

    Optional<CustomerCase> findBySourceSessionIdAndType(String sourceSessionId, CustomerCaseType type);
}
