package io.okagent.module.product.application;

import io.okagent.module.product.domain.SolutionItemRole;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SolutionItemRequest(
        @NotNull UUID productId,
        @Min(1) Integer quantity,
        @NotNull SolutionItemRole role,
        @Min(0) Integer sortOrder) {}
