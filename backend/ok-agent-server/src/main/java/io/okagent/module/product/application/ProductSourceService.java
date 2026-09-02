package io.okagent.module.product.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.module.product.domain.Product;
import io.okagent.module.product.domain.ProductSource;
import io.okagent.module.product.domain.ProductSourceType;
import io.okagent.module.product.domain.ProductStatus;
import io.okagent.module.product.infrastructure.persistence.ProductRepository;
import io.okagent.module.product.infrastructure.persistence.ProductSourceRepository;
import io.okagent.module.model.application.ApiKeyCipher;
import io.okagent.module.product.application.ProductSourceRequest;
import io.okagent.module.product.application.ProductSourceResponse;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Manages external product sources (ERP/CRM/PIM). Connection testing and synchronization delegate
 * to a {@link ProductProvider} selected by source type; built-in manual products have no source.
 */
@Service
public class ProductSourceService {
    private static final Logger log = LoggerFactory.getLogger(ProductSourceService.class);

    private final ProductSourceRepository sources;
    private final ProductRepository products;
    private final ApiKeyCipher cipher;
    private final List<ProductProvider> providers;
    private final ObjectMapper json;

    public ProductSourceService(
            ProductSourceRepository sources,
            ProductRepository products,
            ApiKeyCipher cipher,
            List<ProductProvider> providers,
            ObjectMapper json) {
        this.sources = sources;
        this.products = products;
        this.cipher = cipher;
        this.providers = providers;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Page<ProductSourceResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return sources.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductSourceResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public ProductSourceResponse create(ProductSourceRequest request) {
        validate(request);
        if (sources.existsBySourceKey(request.sourceKey())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "sourceKey already exists: " + request.sourceKey());
        }
        var source = new ProductSource(
                UUID.randomUUID(),
                request.sourceKey().trim(),
                request.name().trim(),
                request.sourceType(),
                request.baseUrl(),
                request.configJson(),
                encryptSecrets(request.secrets()));
        return toResponse(sources.save(source));
    }

    @Transactional
    public ProductSourceResponse update(UUID id, ProductSourceRequest request) {
        validate(request);
        var source = require(id);
        if (!source.getSourceKey().equals(request.sourceKey())
                && sources.existsBySourceKey(request.sourceKey())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "sourceKey already exists: " + request.sourceKey());
        }
        source.update(
                request.sourceKey().trim(),
                request.name().trim(),
                request.sourceType(),
                request.baseUrl(),
                request.configJson(),
                encryptSecrets(request.secrets()));
        return toResponse(sources.save(source));
    }

    @Transactional
    public ProductSourceResponse setEnabled(UUID id, boolean enabled) {
        var source = require(id);
        source.setEnabled(enabled);
        return toResponse(sources.save(source));
    }

    @Transactional
    public void delete(UUID id) {
        sources.deleteById(id);
    }

    @Transactional
    public ProductSourceResponse test(UUID id) {
        var source = require(id);
        ConnectionTestResult result = runTest(toConfig(source));
        source.recordTest(result.success(), result.message());
        return toResponse(sources.save(source));
    }

    @Transactional
    public ProductSourceResponse testDraft(ProductSourceRequest request) {
        validate(request);
        ConnectionTestResult result = runTest(toConfig(request));
        return new ProductSourceResponse(
                null,
                request.sourceKey(),
                request.name(),
                request.sourceType(),
                request.baseUrl(),
                false,
                result.success() ? "SUCCESS" : "FAILED",
                result.message(),
                null,
                null,
                0,
                true,
                null);
    }

    /**
     * Synchronizes remote products into the unified catalog by upserting on (sourceId, externalId).
     * Concrete ProductProvider implementations must be registered for each source type; until then a
     * source of an unsupported type yields a clear error.
     */
    @Transactional
    public int sync(UUID id) {
        var source = require(id);
        var provider = providerFor(source.getSourceType().name());
        if (provider == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No product provider registered for type " + source.getSourceType()
                            + "; external synchronization requires a system-specific provider implementation.");
        }
        List<RemoteProductSummary> remote;
        try {
            remote = provider.listProducts(toConfig(source));
        } catch (Exception e) {
            log.warn("Product sync failed for source {}: {}", source.getSourceKey(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Product sync failed: " + e.getMessage());
        }
        int upserted = 0;
        for (RemoteProductSummary r : remote) {
            if (r.externalId() == null || r.externalId().isBlank() || r.name() == null || r.name().isBlank()) {
                continue;
            }
            Product product = products
                    .findBySourceIdAndExternalId(source.getId(), r.externalId())
                    .orElseGet(() -> new Product(
                            UUID.randomUUID(),
                            "src-" + source.getId().toString().substring(0, 8) + "-" + r.externalId(),
                            r.name().trim()));
            product.markExternal(source.getId(), r.externalId());
            product.apply(
                    r.name(),
                    r.brand(),
                    r.category(),
                    parsePrice(r.priceMin()),
                    parsePrice(r.priceMax()),
                    r.currency(),
                    r.specJson(),
                    r.sellingPoints(),
                    writeList(r.scenarioTags()),
                    writeList(r.imageUrls()),
                    r.description(),
                    ProductStatus.ACTIVE,
                    null);
            products.save(product);
            upserted++;
        }
        source.recordSynced(upserted);
        sources.save(source);
        return upserted;
    }

    public ProductSourceConfig toConfig(ProductSource source) {
        return new ProductSourceConfig(
                source.getSourceKey(),
                source.getSourceType().name(),
                source.getBaseUrl(),
                source.getConfigJson(),
                source.getSecretsCiphertext() == null ? "" : cipher.decrypt(source.getSecretsCiphertext()));
    }

    private ConnectionTestResult runTest(ProductSourceConfig config) {
        if (ProductSourceType.MANUAL.name().equalsIgnoreCase(config.type())) {
            return new ConnectionTestResult(true, "Built-in manual source; no remote connection required.");
        }
        var provider = providerFor(config.type());
        if (provider == null) {
            return new ConnectionTestResult(
                    false, "No product provider registered for type " + config.type() + " yet.");
        }
        try {
            return provider.test(config);
        } catch (Exception e) {
            return new ConnectionTestResult(false, e.getMessage());
        }
    }

    private ProductProvider providerFor(String type) {
        return providers.stream()
                .filter(p -> p.type().equalsIgnoreCase(type))
                .findFirst()
                .orElse(null);
    }

    private void validate(ProductSourceRequest request) {
        if (request.sourceKey() == null || request.sourceKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceKey is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (request.sourceType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceType is required");
        }
    }

    private ProductSource require(UUID id) {
        return sources.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product source not found: " + id));
    }

    private String encryptSecrets(Map<String, String> secrets) {
        if (secrets == null || secrets.isEmpty()) return null;
        try {
            return cipher.encrypt(json.writeValueAsString(secrets));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt source secrets", e);
        }
    }

    private ProductSourceResponse toResponse(ProductSource s) {
        return new ProductSourceResponse(
                s.getId(),
                s.getSourceKey(),
                s.getName(),
                s.getSourceType(),
                s.getBaseUrl(),
                s.getSecretsCiphertext() != null && !s.getSecretsCiphertext().isBlank(),
                s.getLastTestStatus(),
                s.getLastTestMessage(),
                s.getLastTestedAt(),
                s.getLastSyncedAt(),
                s.getProductCount(),
                s.isEnabled(),
                s.getUpdatedAt());
    }

    private ProductSourceConfig toConfig(ProductSourceRequest request) {
        return new ProductSourceConfig(
                request.sourceKey(),
                request.sourceType().name(),
                request.baseUrl(),
                request.configJson(),
                writeStringMap(request.secrets()));
    }

    private String writeStringMap(Map<String, String> map) {
        try {
            return json.writeValueAsString(map == null ? Map.of() : map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String writeList(List<String> list) {
        try {
            return json.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private BigDecimal parsePrice(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Exposed for responses; retained for future structured-config use.
    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) return new HashMap<>();
        try {
            return json.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
