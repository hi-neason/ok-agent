package io.okagent.module.product.infrastructure.persistence;

import io.okagent.module.product.domain.Product;
import io.okagent.module.product.domain.ProductStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findByProductKey(String productKey);

    boolean existsByProductKey(String productKey);

    Optional<Product> findBySourceIdAndExternalId(UUID sourceId, String externalId);

    List<Product> findByStatusOrderByWeightDescUpdatedAtDesc(ProductStatus status, Pageable pageable);

    List<Product> findByCategoryAndStatusOrderByWeightDesc(String category, ProductStatus status);

    List<Product> findAllByIdIn(Collection<UUID> ids);

    List<Product> findBySourceId(UUID sourceId);

    long countBySourceId(UUID sourceId);
}
