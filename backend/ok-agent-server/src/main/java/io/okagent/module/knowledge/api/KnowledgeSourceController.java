package io.okagent.module.knowledge.api;

import io.okagent.module.knowledge.application.*;
import io.okagent.module.knowledge.application.KnowledgeSourceService;
import io.okagent.shared.api.ApiResponse;
import io.okagent.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/knowledge/sources")
public class KnowledgeSourceController {
    private final KnowledgeSourceService service;

    public KnowledgeSourceController(KnowledgeSourceService service) {
        this.service = service;
    }

    /** Lists external knowledge sources, paginated by most-recently-updated. */
    @GetMapping
    public ApiResponse<PageResponse<KnowledgeSourceResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.of(service.list(page, size)));
    }

    /** Returns one knowledge source by id. */
    @GetMapping("/{id}")
    public ApiResponse<KnowledgeSourceResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(service.get(id));
    }

    /** Registers a new external knowledge source. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KnowledgeSourceResponse> create(@Valid @RequestBody KnowledgeSourceRequest request) {
        return ApiResponse.success(service.create(request));
    }

    /** Updates an existing knowledge source; API key changes only when a non-blank value is sent. */
    @PutMapping("/{id}")
    public ApiResponse<KnowledgeSourceResponse> update(
            @PathVariable UUID id, @Valid @RequestBody KnowledgeSourceRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    /** Enables or disables a knowledge source. */
    @PatchMapping("/{id}/enabled")
    public ApiResponse<KnowledgeSourceResponse> setEnabled(@PathVariable UUID id, @RequestParam boolean value) {
        return ApiResponse.success(service.setEnabled(id, value));
    }

    /** Deletes a knowledge source and its catalog items. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    /** Tests a saved source connection and records the result. */
    @PostMapping("/{id}/test")
    public ApiResponse<KnowledgeSourceResponse> test(@PathVariable UUID id) {
        return ApiResponse.success(service.test(id));
    }

    /** Tests an unsaved source draft without persisting it. */
    @PostMapping("/test")
    public ApiResponse<KnowledgeSourceResponse> testDraft(@Valid @RequestBody KnowledgeSourceRequest request) {
        return ApiResponse.success(service.testDraft(request));
    }

    /** Synchronizes the source's remote knowledge bases into the local catalog. */
    @PostMapping("/{id}/sync")
    public ApiResponse<List<KnowledgeCatalogItemResponse>> sync(@PathVariable UUID id) {
        return ApiResponse.success(service.sync(id));
    }

    /** Returns the catalog items discovered for a source. */
    @GetMapping("/{id}/catalog")
    public ApiResponse<List<KnowledgeCatalogItemResponse>> catalog(@PathVariable UUID id) {
        return ApiResponse.success(service.catalogItems(id));
    }

    /** Updates the owner-curated description for a catalog item. */
    @PutMapping("/catalog/{itemId}/description")
    public ApiResponse<KnowledgeCatalogItemResponse> updateDescription(
            @PathVariable UUID itemId, @RequestBody KnowledgeDescriptionUpdateRequest request) {
        return ApiResponse.success(service.updateCatalogDescription(itemId, request.description()));
    }
}
