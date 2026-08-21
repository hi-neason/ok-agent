package io.okagent.domain.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A sales solution / package bundling multiple products. Sales agents recommend solutions for a
 * customer scenario; customer-service agents typically query individual products only.
 */
@Entity
@Table(name = "solution")
public class Solution {
    @Id
    private UUID id;

    @Column(name = "solution_key", nullable = false, unique = true, length = 128)
    private String solutionKey;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String description;

    @Column(name = "target_customer", nullable = false, length = 512)
    private String targetCustomer;

    @Column(nullable = false, length = 512)
    private String scenario;

    @Column(name = "price_note", nullable = false, length = 512)
    private String priceNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SolutionStatus status;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    protected Solution() {}

    public Solution(UUID id, String solutionKey, String name) {
        this.id = id;
        this.solutionKey = solutionKey;
        this.name = name;
        this.targetCustomer = "";
        this.scenario = "";
        this.priceNote = "";
        this.status = SolutionStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void apply(
            String name,
            String description,
            String targetCustomer,
            String scenario,
            String priceNote,
            SolutionStatus status) {
        this.name = name;
        this.description = description;
        this.targetCustomer = targetCustomer == null ? "" : targetCustomer;
        this.scenario = scenario == null ? "" : scenario;
        this.priceNote = priceNote == null ? "" : priceNote;
        this.status = status == null ? SolutionStatus.ACTIVE : status;
        this.updatedAt = Instant.now();
    }

    public void setStatus(SolutionStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public UUID getId() {
        return id;
    }

    public String getSolutionKey() {
        return solutionKey;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getTargetCustomer() {
        return targetCustomer;
    }

    public String getScenario() {
        return scenario;
    }

    public String getPriceNote() {
        return priceNote;
    }

    public SolutionStatus getStatus() {
        return status;
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
