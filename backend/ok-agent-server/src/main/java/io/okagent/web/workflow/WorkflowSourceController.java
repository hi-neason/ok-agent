package io.okagent.web.workflow;

import io.okagent.service.workflow.WorkflowSourceService;
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

    /** Lists all external workflow sources. */
    @GetMapping
    public List<WorkflowSourceResponse> list() {
        return service.list();
    }

    /** Returns one workflow source by id. */
    @GetMapping("/{id}")
    public WorkflowSourceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    /** Registers a new external workflow source. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowSourceResponse create(@Valid @RequestBody WorkflowSourceRequest request) {
        return service.create(request);
    }

    /** Updates an existing workflow source; API key changes only when a non-blank value is sent. */
    @PutMapping("/{id}")
    public WorkflowSourceResponse update(@PathVariable UUID id, @Valid @RequestBody WorkflowSourceRequest request) {
        return service.update(id, request);
    }

    /** Enables or disables a workflow source. */
    @PatchMapping("/{id}/enabled")
    public WorkflowSourceResponse setEnabled(@PathVariable UUID id, @RequestParam boolean value) {
        return service.setEnabled(id, value);
    }

    /** Deletes a workflow source and its catalog items. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    /** Tests a saved source connection and records the result. */
    @PostMapping("/{id}/test")
    public WorkflowSourceResponse test(@PathVariable UUID id) {
        return service.test(id);
    }

    /** Tests an unsaved source draft without persisting it. */
    @PostMapping("/test")
    public WorkflowSourceResponse testDraft(@Valid @RequestBody WorkflowSourceRequest request) {
        return service.testDraft(request);
    }

    /** Synchronizes the source's remote workflows into the local catalog. */
    @PostMapping("/{id}/sync")
    public List<WorkflowCatalogItemResponse> sync(@PathVariable UUID id) {
        return service.sync(id);
    }

    /** Returns the catalog items discovered for a source. */
    @GetMapping("/{id}/catalog")
    public List<WorkflowCatalogItemResponse> catalog(@PathVariable UUID id) {
        return service.catalogItems(id);
    }

    /** Updates the owner-curated description for a catalog item. */
    @PutMapping("/catalog/{itemId}/description")
    public WorkflowCatalogItemResponse updateDescription(
            @PathVariable UUID itemId, @RequestBody WorkflowDescriptionUpdateRequest request) {
        return service.updateCatalogDescription(itemId, request.description());
    }
}
