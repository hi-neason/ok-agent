package io.okagent.module.product.application;

import io.okagent.module.product.domain.SolutionItemRole;
import java.util.UUID;

public record SolutionItemRequest(
        UUID productId, Integer quantity, SolutionItemRole role, Integer sortOrder) {}
