package io.okagent.repository.product;

import io.okagent.domain.product.ProductSource;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductSourceRepository extends JpaRepository<ProductSource, UUID> {
    Optional<ProductSource> findBySourceKey(String sourceKey);

    boolean existsBySourceKey(String sourceKey);
}
