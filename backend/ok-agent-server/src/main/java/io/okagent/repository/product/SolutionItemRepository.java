package io.okagent.repository.product;

import io.okagent.domain.product.SolutionItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolutionItemRepository extends JpaRepository<SolutionItem, UUID> {
    List<SolutionItem> findBySolutionIdOrderBySortOrderAsc(UUID solutionId);

    void deleteBySolutionId(UUID solutionId);
}
