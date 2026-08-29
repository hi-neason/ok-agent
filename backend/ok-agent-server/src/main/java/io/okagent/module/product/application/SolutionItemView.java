package io.okagent.module.product.application;

import java.util.List;

/** A product line inside a solution as exposed to the LLM. */
public record SolutionItemView(
        String productKey,
        String productName,
        int quantity,
        String role,
        int sortOrder) {}
