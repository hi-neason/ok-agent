package io.okagent.domain.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One product line inside a {@link Solution}, with its bundle role and display order. */
@Entity
@Table(name = "solution_item")
public class SolutionItem {
    @Id
    private UUID id;

    @Column(name = "solution_id", nullable = false)
    private UUID solutionId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SolutionItemRole role;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SolutionItem() {}

    public SolutionItem(
            UUID id, UUID solutionId, UUID productId, int quantity, SolutionItemRole role, int sortOrder) {
        this.id = id;
        this.solutionId = solutionId;
        this.productId = productId;
        this.quantity = quantity <= 0 ? 1 : quantity;
        this.role = role == null ? SolutionItemRole.PRIMARY : role;
        this.sortOrder = sortOrder;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSolutionId() {
        return solutionId;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public SolutionItemRole getRole() {
        return role;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
