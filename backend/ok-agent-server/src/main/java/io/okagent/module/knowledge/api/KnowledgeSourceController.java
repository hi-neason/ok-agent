package io.okagent.module.knowledge.api;

import io.okagent.module.knowledge.application.*;
import io.okagent.module.knowledge.application.KnowledgeSourceService;
import io.okagent.shared.api.Response;
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
    public Response<PageResponse<KnowledgeSourceResponse>> list(
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page, @RequestParam(defaultValue = "20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int size) {
        return Response.success(PageResponse.of(service.list(page, size)));
    }

    /** Returns one knowledge source by id. */
    @GetMapping("/{id}")
    public Response<KnowledgeSourceResponse> get(@PathVariable UUID id) {
        return Response.success(service.get(id));
    }

    /** Registers a new external knowledge source. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response<KnowledgeSourceResponse> create(@Valid @RequestBody KnowledgeSourceRequest request) {
        return Response.success(service.create(request));
    }

    /** Updates an existing knowledge source; API key changes only when a non-blank value is sent. */
    @PutMapping("/{id}")
    public Response<KnowledgeSourceResponse> update(
            @PathVariable UUID id, @Valid @RequestBody KnowledgeSourceRequest request) {
        return Response.success(service.update(id, request));
    }

    /** Enables or disables a knowledge source. */
    @PatchMapping("/{id}/enabled")
    public Response<KnowledgeSourceResponse> setEnabled(@PathVariable UUID id, @RequestParam boolean value) {
        return Response.success(service.setEnabled(id, value));
    }

    /** Deletes a knowledge source and its catalog items. */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return Response.success(null);
    }

    /** Tests a saved source connection and records the result. */
    @PostMapping("/{id}/test")
    public Response<KnowledgeSourceResponse> test(@PathVariable UUID id) {
        return Response.success(service.test(id));
    }

    /** Tests an unsaved source draft without persisting it. */
    @PostMapping("/test")
    public Response<KnowledgeSourceResponse> testDraft(@Valid @RequestBody KnowledgeSourceRequest request) {
        return Response.success(service.testDraft(request));
    }

    /** Synchronizes the source's remote knowledge bases into the local catalog. */
    @PostMapping("/{id}/sync")
    public Response<List<KnowledgeCatalogItemResponse>> sync(@PathVariable UUID id) {
        return Response.success(service.sync(id));
    }

    /** Returns the catalog items discovered for a source. */
    @GetMapping("/{id}/catalog")
    public Response<List<KnowledgeCatalogItemResponse>> catalog(@PathVariable UUID id) {
        return Response.success(service.catalogItems(id));
    }

    /** Updates the owner-curated description for a catalog item. */
    @PutMapping("/catalog/{itemId}/description")
    public Response<KnowledgeCatalogItemResponse> updateDescription(
            @PathVariable UUID itemId, @RequestBody KnowledgeDescriptionUpdateRequest request) {
        return Response.success(service.updateCatalogDescription(itemId, request.description()));
    }
}
