package io.okagent.module.product.application;

/** A product record fetched from a remote source during synchronization. */
public record RemoteProductSummary(
        String externalId,
        String name,
        String brand,
        String category,
        String priceMin,
        String priceMax,
        String currency,
        String specJson,
        String sellingPoints,
        java.util.List<String> scenarioTags,
        java.util.List<String> imageUrls,
        String description) {}
