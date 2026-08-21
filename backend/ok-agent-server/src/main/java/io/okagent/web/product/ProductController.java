package io.okagent.web.product;

import io.okagent.domain.product.ProductStatus;
import io.okagent.service.product.ProductService;
import jakarta.validation.Valid;
import java.util.List;
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

    /** Lists all products in the unified catalog. */
    @GetMapping
    public List<ProductResponse> list() {
        return service.list();
    }

    /** Returns one product by id. */
    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    /** Creates a new product (built-in manual catalog entry). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return service.create(request);
    }

    /** Updates an existing product. */
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return service.update(id, request);
    }

    /** Enables or disables (discontinues) a product. */
    @PatchMapping("/{id}/status")
    public ProductResponse setStatus(@PathVariable UUID id, @RequestParam ProductStatus status) {
        return service.setStatus(id, status);
    }

    /** Deletes a product. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
