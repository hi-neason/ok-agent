package io.okagent.web.product;

import io.okagent.service.product.ProductSourceService;
import io.okagent.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
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
    public PageResponse<ProductSourceResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.list(page, size));
    }

    /** Returns one product source by id. */
    @GetMapping("/{id}")
    public ProductSourceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    /** Registers a new external product source. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductSourceResponse create(@Valid @RequestBody ProductSourceRequest request) {
        return service.create(request);
    }

    /** Updates an existing product source; secrets change only when non-empty values are sent. */
    @PutMapping("/{id}")
    public ProductSourceResponse update(@PathVariable UUID id, @Valid @RequestBody ProductSourceRequest request) {
        return service.update(id, request);
    }

    /** Enables or disables a product source. */
    @PatchMapping("/{id}/enabled")
    public ProductSourceResponse setEnabled(@PathVariable UUID id, @RequestParam boolean value) {
        return service.setEnabled(id, value);
    }

    /** Deletes a product source (its synced products keep source_id via ON DELETE SET NULL). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    /** Tests a saved source connection and records the result. */
    @PostMapping("/{id}/test")
    public ProductSourceResponse test(@PathVariable UUID id) {
        return service.test(id);
    }

    /** Tests an unsaved source draft without persisting it. */
    @PostMapping("/test")
    public ProductSourceResponse testDraft(@Valid @RequestBody ProductSourceRequest request) {
        return service.testDraft(request);
    }

    /** Synchronizes remote products from the source into the unified catalog. */
    @PostMapping("/{id}/sync")
    public SyncResult sync(@PathVariable UUID id) {
        return new SyncResult(service.sync(id));
    }
}
