package io.okagent.service.product;

import java.util.List;

/** A product line inside a solution as exposed to the LLM. */
public record SolutionItemView(
        String productKey,
        String productName,
        int quantity,
        String role,
        int sortOrder) {}
