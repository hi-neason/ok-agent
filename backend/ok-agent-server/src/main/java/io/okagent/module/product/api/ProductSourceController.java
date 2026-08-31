package io.okagent.module.product.api;

import io.okagent.module.product.application.*;
import io.okagent.module.product.application.ProductSourceService;
import io.okagent.shared.api.Response;
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
    public Response<PageResponse<ProductSourceResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return Response.success(PageResponse.of(service.list(page, size)));
    }

    /** Returns one product source by id. */
    @GetMapping("/{id}")
    public Response<ProductSourceResponse> get(@PathVariable UUID id) {
        return Response.success(service.get(id));
    }

    /** Registers a new external product source. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response<ProductSourceResponse> create(@Valid @RequestBody ProductSourceRequest request) {
        return Response.success(service.create(request));
    }

    /** Updates an existing product source; secrets change only when non-empty values are sent. */
    @PutMapping("/{id}")
    public Response<ProductSourceResponse> update(
            @PathVariable UUID id, @Valid @RequestBody ProductSourceRequest request) {
        return Response.success(service.update(id, request));
    }

    /** Enables or disables a product source. */
    @PatchMapping("/{id}/enabled")
    public Response<ProductSourceResponse> setEnabled(@PathVariable UUID id, @RequestParam boolean value) {
        return Response.success(service.setEnabled(id, value));
    }

    /** Deletes a product source (its synced products keep source_id via ON DELETE SET NULL). */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return Response.success(null);
    }

    /** Tests a saved source connection and records the result. */
    @PostMapping("/{id}/test")
    public Response<ProductSourceResponse> test(@PathVariable UUID id) {
        return Response.success(service.test(id));
    }

    /** Tests an unsaved source draft without persisting it. */
    @PostMapping("/test")
    public Response<ProductSourceResponse> testDraft(@Valid @RequestBody ProductSourceRequest request) {
        return Response.success(service.testDraft(request));
    }

    /** Synchronizes remote products from the source into the unified catalog. */
    @PostMapping("/{id}/sync")
    public Response<SyncResult> sync(@PathVariable UUID id) {
        return Response.success(new SyncResult(service.sync(id)));
    }
}
