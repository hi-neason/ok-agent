package io.okagent.service.product;

import io.okagent.domain.product.Product;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A product as exposed to the LLM at runtime, with a deterministic recall score. */
public record RankedProduct(
        UUID id,
        String productKey,
        String name,
        String brand,
        String category,
        BigDecimal priceMin,
        BigDecimal priceMax,
        String currency,
        String specJson,
        String sellingPoints,
        List<String> scenarioTags,
        String description,
        double score) {

    static RankedProduct from(Product p, double score, List<String> tags) {
        return new RankedProduct(
                p.getId(),
                p.getProductKey(),
                p.getName(),
                p.getBrand(),
                p.getCategory(),
                p.getPriceMin(),
                p.getPriceMax(),
                p.getCurrency(),
                p.getSpecJson(),
                p.getSellingPoints(),
                tags,
                p.getDescription(),
                score);
    }
}
