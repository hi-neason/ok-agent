package io.okagent.web.product;

import io.okagent.domain.product.SolutionItemRole;
import io.okagent.domain.product.SolutionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SolutionResponse(
        UUID id,
        String solutionKey,
        String name,
        String description,
        String targetCustomer,
        String scenario,
        String priceNote,
        SolutionStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<Item> items) {

    public record Item(
            UUID id,
            UUID productId,
            String productKey,
            String productName,
            int quantity,
            SolutionItemRole role,
            int sortOrder) {}
}
