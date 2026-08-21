package io.okagent.repository.product;

import io.okagent.domain.product.Product;
import io.okagent.domain.product.ProductStatus;
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
