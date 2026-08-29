package io.okagent.module.workflow.api;

import io.okagent.module.workflow.application.*;
import io.okagent.module.workflow.application.WorkflowSourceService;
import io.okagent.shared.api.Response;
import io.okagent.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workflow/sources")
public class WorkflowSourceController {
    private final WorkflowSourceService service;

    public WorkflowSourceController(WorkflowSourceService service) {
        this.service = service;
    }

    /** Lists external workflow sources, newest first, paged. */
    @GetMapping
    public Response<PageResponse<WorkflowSourceResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return Response.success(PageResponse.of(service.list(page, size)));
    }

    /** Returns one workflow source by id. */
    @GetMapping("/{id}")
    public Response<WorkflowSourceResponse> get(@PathVariable UUID id) {
        return Response.success(service.get(id));
    }

    /** Registers a new external workflow source. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response<WorkflowSourceResponse> create(@Valid @RequestBody WorkflowSourceRequest request) {
        return Response.success(service.create(request));
    }

    /** Updates an existing workflow source; API key changes only when a non-blank value is sent. */
    @PutMapping("/{id}")
    public Response<WorkflowSourceResponse> update(
            @PathVariable UUID id, @Valid @RequestBody WorkflowSourceRequest request) {
        return Response.success(service.update(id, request));
    }

    /** Enables or disables a workflow source. */
    @PatchMapping("/{id}/enabled")
    public Response<WorkflowSourceResponse> setEnabled(@PathVariable UUID id, @RequestParam boolean value) {
        return Response.success(service.setEnabled(id, value));
    }

    /** Deletes a workflow source and its catalog items. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Response<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return Response.success(null);
    }

    /** Tests a saved source connection and records the result. */
    @PostMapping("/{id}/test")
    public Response<WorkflowSourceResponse> test(@PathVariable UUID id) {
        return Response.success(service.test(id));
    }

    /** Tests an unsaved source draft without persisting it. */
    @PostMapping("/test")
    public Response<WorkflowSourceResponse> testDraft(@Valid @RequestBody WorkflowSourceRequest request) {
        return Response.success(service.testDraft(request));
    }

    /** Synchronizes the source's remote workflows into the local catalog. */
    @PostMapping("/{id}/sync")
    public Response<List<WorkflowCatalogItemResponse>> sync(@PathVariable UUID id) {
        return Response.success(service.sync(id));
    }

    /** Returns the catalog items discovered for a source. */
    @GetMapping("/{id}/catalog")
    public Response<List<WorkflowCatalogItemResponse>> catalog(@PathVariable UUID id) {
        return Response.success(service.catalogItems(id));
    }

    /** Updates the owner-curated description for a catalog item. */
    @PutMapping("/catalog/{itemId}/description")
    public Response<WorkflowCatalogItemResponse> updateDescription(
            @PathVariable UUID itemId, @RequestBody WorkflowDescriptionUpdateRequest request) {
        return Response.success(service.updateCatalogDescription(itemId, request.description()));
    }
}
