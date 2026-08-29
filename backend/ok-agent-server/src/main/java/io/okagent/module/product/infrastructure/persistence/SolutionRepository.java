package io.okagent.module.product.infrastructure.persistence;

import io.okagent.module.product.domain.Solution;
import io.okagent.module.product.domain.SolutionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolutionRepository extends JpaRepository<Solution, UUID> {
    Optional<Solution> findBySolutionKey(String solutionKey);

    boolean existsBySolutionKey(String solutionKey);

    List<Solution> findByStatusOrderByUpdatedAtDesc(SolutionStatus status);
}
