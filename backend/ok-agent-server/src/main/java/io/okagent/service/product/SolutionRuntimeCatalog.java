package io.okagent.service.product;

import io.okagent.domain.product.Product;
import io.okagent.domain.product.Solution;
import io.okagent.domain.product.SolutionItem;
import io.okagent.domain.product.SolutionStatus;
import io.okagent.repository.product.ProductRepository;
import io.okagent.repository.product.SolutionItemRepository;
import io.okagent.repository.product.SolutionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Read-only runtime view of active solutions and their bundled products. Solutions are global
 * assets (visible to any agent granted the SOLUTION capability); per-agent product visibility does
 * not restrict which solutions an agent may read.
 */
@Service
public class SolutionRuntimeCatalog {
    private final SolutionRepository solutions;
    private final SolutionItemRepository items;
    private final ProductRepository products;

    public SolutionRuntimeCatalog(
            SolutionRepository solutions, SolutionItemRepository items, ProductRepository products) {
        this.solutions = solutions;
        this.items = items;
        this.products = products;
    }

    /** Lists all active solutions with their bundled product lines. */
    public List<RankedSolution> listActive() {
        return solutions.findByStatusOrderByUpdatedAtDesc(SolutionStatus.ACTIVE).stream()
                .map(s -> RankedSolution.of(s, itemsFor(s)).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** Resolves one active solution by its solutionKey or UUID. */
    public Optional<RankedSolution> getByKey(String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        Solution s = solutions.findBySolutionKey(key).orElse(null);
        if (s == null) {
            try {
                s = solutions.findById(UUID.fromString(key)).orElse(null);
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        if (s == null || s.getStatus() != SolutionStatus.ACTIVE) return Optional.empty();
        return RankedSolution.of(s, itemsFor(s));
    }

    private List<SolutionItemView> itemsFor(Solution solution) {
        List<SolutionItem> rows = items.findBySolutionIdOrderBySortOrderAsc(solution.getId());
        Map<UUID, Product> byId = new HashMap<>();
        products.findAllByIdIn(rows.stream().map(SolutionItem::getProductId).toList())
                .forEach(p -> byId.put(p.getId(), p));
        List<SolutionItemView> out = new ArrayList<>(rows.size());
        for (SolutionItem row : rows) {
            Product p = byId.get(row.getProductId());
            String name = p == null ? "(missing)" : p.getName();
            String pKey = p == null ? row.getProductId().toString() : p.getProductKey();
            out.add(new SolutionItemView(
                    pKey, name, row.getQuantity(), row.getRole().name(), row.getSortOrder()));
        }
        return out;
    }
}
