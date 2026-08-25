package io.okagent.repository.customerwork;

import io.okagent.domain.customerwork.CustomerCase;
import io.okagent.domain.customerwork.CustomerCaseType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerCaseRepository extends JpaRepository<CustomerCase, UUID> {
    List<CustomerCase> findBySourceSessionIdOrderByCreatedAtAsc(String sourceSessionId);

    Optional<CustomerCase> findBySourceSessionIdAndType(String sourceSessionId, CustomerCaseType type);
}
