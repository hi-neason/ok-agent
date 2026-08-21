package io.okagent.web.product;

import io.okagent.domain.product.SolutionItemRole;
import java.util.UUID;

public record SolutionItemRequest(
        UUID productId, Integer quantity, SolutionItemRole role, Integer sortOrder) {}
