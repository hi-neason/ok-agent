package io.okagent.module.product.api;

import io.okagent.module.product.application.*;
import io.okagent.module.product.application.ProductService;
import io.okagent.module.product.domain.ProductStatus;
import io.okagent.shared.api.ApiResponse;
import io.okagent.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    /** Lists products in the unified catalog, paginated by most-recently-updated. */
    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.of(service.list(page, size)));
    }

    /** Returns one product by id. */
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(service.get(id));
    }

    /** Creates a new product (built-in manual catalog entry). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.success(service.create(request));
    }

    /** Updates an existing product. */
    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    /** Enables or disables (discontinues) a product. */
    @PatchMapping("/{id}/status")
    public ApiResponse<ProductResponse> setStatus(@PathVariable UUID id, @RequestParam ProductStatus status) {
        return ApiResponse.success(service.setStatus(id, status));
    }

    /** Deletes a product. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
