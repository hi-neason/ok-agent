package io.okagent.web.product;

import io.okagent.domain.product.SolutionItemRole;
import io.okagent.domain.product.SolutionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SolutionRequest(
        @NotBlank @Size(max = 128) String solutionKey,
        @NotBlank @Size(max = 255) String name,
        String description,
        @Size(max = 512) String targetCustomer,
        @Size(max = 512) String scenario,
        @Size(max = 512) String priceNote,
        SolutionStatus status,
        List<SolutionItemRequest> items) {}
