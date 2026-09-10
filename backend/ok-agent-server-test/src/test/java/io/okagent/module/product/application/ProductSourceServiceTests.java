package io.okagent.module.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.module.model.application.ApiKeyCipher;
import io.okagent.module.product.domain.Product;
import io.okagent.module.product.domain.ProductSource;
import io.okagent.module.product.domain.ProductSourceType;
import io.okagent.module.product.domain.ProductStatus;
import io.okagent.module.product.infrastructure.persistence.ProductRepository;
import io.okagent.module.product.infrastructure.persistence.ProductSourceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductSourceServiceTests {
    @Test
    void syncDiscontinuesProductsMissingFromRemoteSnapshot() {
        UUID sourceId = UUID.randomUUID();
        ProductSource source = new ProductSource(
                sourceId,
                "catalog",
                "Catalog",
                ProductSourceType.HTTP,
                "https://catalog.example",
                "{}",
                null);
        Product kept = externalProduct(sourceId, "kept");
        Product stale = externalProduct(sourceId, "stale");

        ProductSourceRepository sources = mock(ProductSourceRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        ProductProvider provider = new ProductProvider() {
            @Override public String type() {
                return ProductSourceType.HTTP.name();
            }

            @Override public ConnectionTestResult test(ProductSourceConfig config) {
                return new ConnectionTestResult(true, "ok");
            }

            @Override public List<RemoteProductSummary> listProducts(ProductSourceConfig config) {
                return List.of(new RemoteProductSummary(
                        "kept",
                        "Kept",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        null));
            }
        };

        when(sources.findById(sourceId)).thenReturn(Optional.of(source));
        when(products.findBySourceIdAndExternalId(sourceId, "kept")).thenReturn(Optional.of(kept));
        when(products.findBySourceId(sourceId)).thenReturn(List.of(kept, stale));

        ProductSourceService service = new ProductSourceService(
                sources,
                products,
                mock(ApiKeyCipher.class),
                List.of(provider),
                new ObjectMapper());

        assertThat(service.sync(sourceId)).isEqualTo(1);
        assertThat(kept.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(stale.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
        verify(products).save(stale);
        verify(sources).save(source);
    }

    private static Product externalProduct(UUID sourceId, String externalId) {
        Product product = new Product(UUID.randomUUID(), "src-" + externalId, externalId);
        product.markExternal(sourceId, externalId);
        product.apply(
                externalId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                ProductStatus.ACTIVE,
                null);
        return product;
    }
}
