package io.okagent.module.product.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.module.product.domain.Product;
import io.okagent.module.product.domain.ProductStatus;
import io.okagent.module.product.infrastructure.persistence.ProductRepository;
import io.okagent.module.product.application.ProductRequest;
import io.okagent.module.product.application.ProductResponse;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Service
public class ProductService {
    private final ProductRepository products;
    private final ObjectMapper json;

    public ProductService(ProductRepository products, ObjectMapper json) {
        this.products = products;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return products.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (request.productKey() == null || request.productKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productKey is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (products.existsByProductKey(request.productKey())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "productKey already exists: " + request.productKey());
        }
        var product = new Product(UUID.randomUUID(), request.productKey().trim(), request.name().trim());
        apply(product, request, "system");
        return toResponse(products.save(product));
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        var product = require(id);
        if (!product.getProductKey().equals(request.productKey())
                && products.existsByProductKey(request.productKey())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "productKey already exists: " + request.productKey());
        }
        apply(product, request, "system");
        return toResponse(products.save(product));
    }

    @Transactional
    public ProductResponse setStatus(UUID id, ProductStatus status) {
        var product = require(id);
        product.setStatus(status);
        return toResponse(products.save(product));
    }

    @Transactional
    public void delete(UUID id) {
        products.deleteById(id);
    }

    private Product require(UUID id) {
        return products.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id));
    }

    private void apply(Product product, ProductRequest req, String updatedBy) {
        product.apply(
                req.name() == null ? product.getName() : req.name().trim(),
                req.brand(),
                req.category(),
                req.priceMin(),
                req.priceMax(),
                req.currency(),
                writeJson(req.spec()),
                req.sellingPoints(),
                writeList(req.scenarioTags()),
                writeList(req.imageUrls()),
                req.description(),
                req.status(),
                req.weight());
        product.setUpdatedBy(updatedBy);
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getProductKey(),
                p.getSourceId(),
                p.getExternalId(),
                p.getName(),
                p.getBrand(),
                p.getCategory(),
                p.getPriceMin(),
                p.getPriceMax(),
                p.getCurrency(),
                readMap(p.getSpecJson()),
                p.getSellingPoints(),
                readList(p.getScenarioTagsJson()),
                readList(p.getImagesJson()),
                p.getDescription(),
                p.getStatus(),
                p.getWeight(),
                p.getVersion(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }

    private String writeJson(Map<String, Object> map) {
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

    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) return new HashMap<>();
        try {
            return json.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private List<String> readList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return json.readValue(value, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
