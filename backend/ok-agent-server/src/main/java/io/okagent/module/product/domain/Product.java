package io.okagent.module.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A sellable product/SKU in the unified product catalog. Built-in products have sourceId NULL;
 * externally synchronized products carry sourceId + externalId (unique pair). Structured fields
 * (category, price band, scenario tags) power deterministic rule recall; free-form description and
 * selling_points ground the LLM's recommendation explanation.
 */
@Entity
@Table(name = "product")
public class Product {
    @Id
    private UUID id;

    @Column(name = "product_key", nullable = false, unique = true, length = 128)
    private String productKey;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 128)
    private String brand;

    @Column(nullable = false, length = 128)
    private String category;

    @Column(name = "price_min", precision = 14, scale = 2)
    private BigDecimal priceMin;

    @Column(name = "price_max", precision = 14, scale = 2)
    private BigDecimal priceMax;

    @Column(nullable = false, length = 8)
    private String currency;

    /** Free-form JSON object of category-specific specifications. */
    @Column(name = "spec_json", nullable = false, columnDefinition = "TEXT")
    private String specJson;

    @Column(name = "selling_points", columnDefinition = "MEDIUMTEXT")
    private String sellingPoints;

    /** JSON array of scenario tags, e.g. ["small-business","self-hosted"]. */
    @Column(name = "scenario_tags_json", nullable = false, columnDefinition = "TEXT")
    private String scenarioTagsJson;

    /** JSON array of image URLs. */
    @Column(name = "images_json", nullable = false, columnDefinition = "TEXT")
    private String imagesJson;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProductStatus status;

    /** Sorting/ranking weight (higher = promoted more strongly in recall). */
    @Column(nullable = false)
    private int weight;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    protected Product() {}

    public Product(UUID id, String productKey, String name) {
        this.id = id;
        this.productKey = productKey;
        this.name = name;
        this.brand = "";
        this.category = "";
        this.currency = "CNY";
        this.specJson = "{}";
        this.scenarioTagsJson = "[]";
        this.imagesJson = "[]";
        this.status = ProductStatus.ACTIVE;
        this.weight = 100;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void apply(
            String name,
            String brand,
            String category,
            BigDecimal priceMin,
            BigDecimal priceMax,
            String currency,
            String specJson,
            String sellingPoints,
            String scenarioTagsJson,
            String imagesJson,
            String description,
            ProductStatus status,
            Integer weight) {
        this.name = name;
        this.brand = brand == null ? "" : brand;
        this.category = category == null ? "" : category;
        this.priceMin = priceMin;
        this.priceMax = priceMax;
        this.currency = currency == null || currency.isBlank() ? "CNY" : currency;
        this.specJson = specJson == null || specJson.isBlank() ? "{}" : specJson;
        this.sellingPoints = sellingPoints;
        this.scenarioTagsJson = scenarioTagsJson == null || scenarioTagsJson.isBlank() ? "[]" : scenarioTagsJson;
        this.imagesJson = imagesJson == null || imagesJson.isBlank() ? "[]" : imagesJson;
        this.description = description;
        this.status = status == null ? ProductStatus.ACTIVE : status;
        if (weight != null) this.weight = weight;
        this.updatedAt = Instant.now();
    }

    public void markExternal(UUID sourceId, String externalId) {
        this.sourceId = sourceId;
        this.externalId = externalId;
        this.updatedAt = Instant.now();
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public UUID getId() {
        return id;
    }

    public String getProductKey() {
        return productKey;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getPriceMin() {
        return priceMin;
    }

    public BigDecimal getPriceMax() {
        return priceMax;
    }

    public String getCurrency() {
        return currency;
    }

    public String getSpecJson() {
        return specJson;
    }

    public String getSellingPoints() {
        return sellingPoints;
    }

    public String getScenarioTagsJson() {
        return scenarioTagsJson;
    }

    public String getImagesJson() {
        return imagesJson;
    }

    public String getDescription() {
        return description;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public int getWeight() {
        return weight;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
