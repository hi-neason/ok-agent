package io.okagent.service.product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** A solution/package as exposed to the LLM, with its bundled product lines. */
public record RankedSolution(
        UUID id,
        String solutionKey,
        String name,
        String description,
        String targetCustomer,
        String scenario,
        String priceNote,
        List<SolutionItemView> items) {

    static Optional<RankedSolution> of(
            io.okagent.domain.product.Solution s, List<SolutionItemView> items) {
        if (s == null) return Optional.empty();
        return Optional.of(new RankedSolution(
                s.getId(),
                s.getSolutionKey(),
                s.getName(),
                s.getDescription(),
                s.getTargetCustomer(),
                s.getScenario(),
                s.getPriceNote(),
                items));
    }
}
