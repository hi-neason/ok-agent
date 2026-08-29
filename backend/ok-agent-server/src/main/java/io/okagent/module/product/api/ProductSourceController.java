package io.okagent.module.product.api;

import io.okagent.module.product.application.*;
import io.okagent.module.product.application.ProductSourceService;
import io.okagent.shared.api.ApiResponse;
import io.okagent.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/product-sources")
public class ProductSourceController {
    private final ProductSourceService service;

    public ProductSourceController(ProductSourceService service) {
        this.service = service;
    }

    /** Lists external product sources, paginated by most-recently-updated. */
    @GetMapping
    public ApiResponse<PageResponse<ProductSourceResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.of(service.list(page, size)));
    }

    /** Returns one product source by id. */
    @GetMapping("/{id}")
    public ApiResponse<ProductSourceResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(service.get(id));
    }

    /** Registers a new external product source. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductSourceResponse> create(@Valid @RequestBody ProductSourceRequest request) {
        return ApiResponse.success(service.create(request));
    }

    /** Updates an existing product source; secrets change only when non-empty values are sent. */
    @PutMapping("/{id}")
    public ApiResponse<ProductSourceResponse> update(
            @PathVariable UUID id, @Valid @RequestBody ProductSourceRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    /** Enables or disables a product source. */
    @PatchMapping("/{id}/enabled")
    public ApiResponse<ProductSourceResponse> setEnabled(@PathVariable UUID id, @RequestParam boolean value) {
        return ApiResponse.success(service.setEnabled(id, value));
    }

    /** Deletes a product source (its synced products keep source_id via ON DELETE SET NULL). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    /** Tests a saved source connection and records the result. */
    @PostMapping("/{id}/test")
    public ApiResponse<ProductSourceResponse> test(@PathVariable UUID id) {
        return ApiResponse.success(service.test(id));
    }

    /** Tests an unsaved source draft without persisting it. */
    @PostMapping("/test")
    public ApiResponse<ProductSourceResponse> testDraft(@Valid @RequestBody ProductSourceRequest request) {
        return ApiResponse.success(service.testDraft(request));
    }

    /** Synchronizes remote products from the source into the unified catalog. */
    @PostMapping("/{id}/sync")
    public ApiResponse<SyncResult> sync(@PathVariable UUID id) {
        return ApiResponse.success(new SyncResult(service.sync(id)));
    }
}
