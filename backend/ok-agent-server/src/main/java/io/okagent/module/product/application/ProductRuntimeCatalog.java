package io.okagent.module.product.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.module.product.domain.AgentProductBinding;
import io.okagent.module.product.domain.Product;
import io.okagent.module.product.domain.ProductBindingScope;
import io.okagent.module.product.domain.ProductStatus;
import io.okagent.module.product.infrastructure.persistence.AgentProductBindingRepository;
import io.okagent.module.product.infrastructure.persistence.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runtime-facing catalog used by the product tools. Resolves an agent's binding to determine the
 * visible product set, performs deterministic rule recall (filters + keyword match), and applies a
 * transparent weighted score (budget fit, tag overlap, base weight). The LLM performs the final
 * selection and explanation on the returned shortlist.
 */
@Service
public class ProductRuntimeCatalog {
    private static final Logger log = LoggerFactory.getLogger(ProductRuntimeCatalog.class);
    private static final int DEFAULT_LIMIT = 10;
    private static final int HARD_LIMIT = 50;

    private final ProductRepository products;
    private final AgentProductBindingRepository bindings;
    private final ObjectMapper json = new ObjectMapper();

    public ProductRuntimeCatalog(ProductRepository products, AgentProductBindingRepository bindings) {
        this.products = products;
        this.bindings = bindings;
    }

    /** Returns true when the agent has an enabled product binding (drives toolkit registration). */
    public boolean hasProducts(UUID agentId) {
        var binding = bindings.findByAgentId(agentId).orElse(null);
        if (binding == null || !binding.isEnabled()) return false;
        return !visibleProducts(agentId).isEmpty();
    }

    /** Returns the capabilities granted to an agent (empty set when no binding / disabled). */
    public Set<String> capabilities(UUID agentId) {
        return bindings.findByAgentId(agentId)
                .filter(AgentProductBinding::isEnabled)
                .map(b -> readStringList(b.getCapabilitiesJson()))
                .map(HashSet::new)
                .map(s -> (Set<String>) s)
                .orElse(Set.of());
    }

    /** Lists visible active products (paged by weight), used by the simple search/list tool. */
    public List<RankedProduct> list(UUID agentId, int limit) {
        int safeLimit = clampLimit(limit);
        return visibleProducts(agentId).stream()
                .sorted(Comparator.comparingInt(Product::getWeight).reversed())
                .limit(safeLimit)
                .map(p -> RankedProduct.from(p, p.getWeight(), readTags(p)))
                .toList();
    }

    /** Returns a single visible product by id, or empty when not visible / not found. */
    public Optional<RankedProduct> get(UUID agentId, UUID productId) {
        return visibleProducts(agentId).stream()
                .filter(p -> p.getId().equals(productId))
                .findFirst()
                .map(p -> RankedProduct.from(p, p.getWeight(), readTags(p)));
    }

    /** Resolves a product by its productKey or UUID string within the agent's visible set. */
    public Optional<RankedProduct> getByKeyOrId(UUID agentId, String productId) {
        return visibleProducts(agentId).stream()
                .filter(p -> p.getProductKey().equals(productId) || p.getId().toString().equals(productId))
                .findFirst()
                .map(p -> RankedProduct.from(p, p.getWeight(), readTags(p)));
    }

    /**
     * Rule recall + weighted ranking. Applies deterministic filters (category, price band, scenario
     * tag, keyword) then scores candidates on budget fit, tag overlap, keyword hits and base weight.
     * Returns at most {@code limit} products, highest score first.
     */
    public List<RankedProduct> recommend(
            UUID agentId,
            String query,
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<String> tags,
            int limit) {
        int safeLimit = clampLimit(limit);
        Set<String> wantedTags = normalizeTags(tags);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        List<RankedProduct> scored = new ArrayList<>();
        for (Product p : visibleProducts(agentId)) {
            if (category != null && !category.isBlank()
                    && !category.equalsIgnoreCase(p.getCategory())) {
                continue;
            }
            if (minPrice != null && p.getPriceMax() != null
                    && p.getPriceMax().compareTo(minPrice) < 0) {
                continue;
            }
            if (maxPrice != null && p.getPriceMin() != null
                    && p.getPriceMin().compareTo(maxPrice) > 0) {
                continue;
            }
            List<String> pTags = readTags(p);
            if (!wantedTags.isEmpty() && pTags.stream().noneMatch(wantedTags::contains)) {
                continue;
            }
            double score = score(p, q, wantedTags, minPrice, maxPrice);
            scored.add(RankedProduct.from(p, score, pTags));
        }
        scored.sort(Comparator.comparingDouble(RankedProduct::score).reversed());
        return scored.size() <= safeLimit ? scored : scored.subList(0, safeLimit);
    }

    /** Loads all active products visible to an agent per its binding scope. */
    public List<Product> visibleProducts(UUID agentId) {
        var binding = bindings.findByAgentId(agentId).orElse(null);
        if (binding == null || !binding.isEnabled() || binding.getScope() == ProductBindingScope.NONE) {
            return List.of();
        }
        return switch (binding.getScope()) {
            case ALL -> products.findAll().stream()
                    .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                    .toList();
            case CATEGORY -> {
                String cat = binding.getScopeValue();
                yield cat == null ? List.of() : products.findByCategoryAndStatusOrderByWeightDesc(
                        cat, ProductStatus.ACTIVE);
            }
            case TAG -> {
                Set<String> wanted = readStringList(binding.getScopeValue()).stream()
                        .map(s -> s.toLowerCase(Locale.ROOT))
                        .collect(java.util.stream.Collectors.toSet());
                yield wanted.isEmpty()
                        ? List.of()
                        : products.findAll().stream()
                                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                                .filter(p -> readTags(p).stream().anyMatch(wanted::contains))
                                .toList();
            }
            case EXPLICIT -> {
                Set<UUID> ids = readUuidList(binding.getScopeValue());
                yield ids.isEmpty()
                        ? List.of()
                        : products.findAllByIdIn(ids).stream()
                                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                                .toList();
            }
            case NONE -> List.of();
        };
    }

    private double score(
            Product p, String query, Set<String> wantedTags, BigDecimal minPrice, BigDecimal maxPrice) {
        double score = 0;
        // Base weight contributes a small, bounded amount (0..10).
        score += Math.min(p.getWeight(), 1000) / 100.0;

        List<String> tags = readTags(p);
        if (!wantedTags.isEmpty()) {
            long overlap = tags.stream().filter(wantedTags::contains).count();
            score += overlap * 5.0;
        }

        // Budget fit: reward products whose price band overlaps the requested budget.
        BigDecimal lo = p.getPriceMin();
        BigDecimal hi = p.getPriceMax() == null ? lo : p.getPriceMax();
        if (lo != null && minPrice != null && maxPrice != null) {
            BigDecimal clampedLo = lo.max(minPrice);
            BigDecimal clampedHi = hi.min(maxPrice);
            if (clampedHi.compareTo(clampedLo) >= 0) {
                score += 8.0;
                // Prefer products whose midpoint sits inside the budget.
                BigDecimal mid = lo.add(hi).divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
                if (mid.compareTo(minPrice) >= 0 && mid.compareTo(maxPrice) <= 0) {
                    score += 4.0;
                }
            }
        }

        // Keyword relevance (substring match on the key text fields).
        if (!query.isEmpty()) {
            String haystack = (p.getName() + " " + p.getBrand() + " " + p.getCategory() + " "
                            + safe(p.getSellingPoints()) + " " + safe(p.getDescription()))
                    .toLowerCase(Locale.ROOT);
            if (haystack.contains(query)) score += 6.0;
            for (String token : query.split("\\s+")) {
                if (token.length() > 1 && haystack.contains(token)) score += 1.0;
            }
        }
        return score;
    }

    private List<String> readTags(Product p) {
        return readStringList(p.getScenarioTagsJson());
    }

    private Set<String> normalizeTags(List<String> tags) {
        if (tags == null) return Set.of();
        return tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
    }

    private List<String> readStringList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return json.readValue(value, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Set<UUID> readUuidList(String value) {
        List<String> strings = readStringList(value);
        Set<UUID> out = new HashSet<>();
        for (String s : strings) {
            try {
                out.add(UUID.fromString(s.trim()));
            } catch (IllegalArgumentException ignored) {
                // skip malformed
            }
        }
        return out;
    }

    private int clampLimit(int limit) {
        if (limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, HARD_LIMIT);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
