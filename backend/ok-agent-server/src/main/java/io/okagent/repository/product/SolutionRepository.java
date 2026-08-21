package io.okagent.repository.product;

import io.okagent.domain.product.Solution;
import io.okagent.domain.product.SolutionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolutionRepository extends JpaRepository<Solution, UUID> {
    Optional<Solution> findBySolutionKey(String solutionKey);

    boolean existsBySolutionKey(String solutionKey);

    List<Solution> findByStatusOrderByUpdatedAtDesc(SolutionStatus status);
}
