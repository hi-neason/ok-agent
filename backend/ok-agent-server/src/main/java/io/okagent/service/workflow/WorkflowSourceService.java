package io.okagent.service.workflow;

import io.okagent.web.workflow.WorkflowCatalogItemResponse;
import io.okagent.web.workflow.WorkflowSourceRequest;
import io.okagent.web.workflow.WorkflowSourceResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** CRUD, connection testing, and catalog synchronization for external workflow sources. */
public interface WorkflowSourceService {

    /** Lists all workflow sources, newest first, paged. */
    Page<WorkflowSourceResponse> list(int page, int size);

    /** Returns one source by id. */
    WorkflowSourceResponse get(UUID id);

    /** Creates a new source (does not test or sync automatically). */
    WorkflowSourceResponse create(WorkflowSourceRequest request);

    /** Updates an existing source; API key is only changed when a non-blank value is supplied. */
    WorkflowSourceResponse update(UUID id, WorkflowSourceRequest request);

    /** Enables or disables a source. */
    WorkflowSourceResponse setEnabled(UUID id, boolean enabled);

    /** Deletes a source and its catalog items (cascaded). */
    void delete(UUID id);

    /** Tests a saved source and records the result. */
    WorkflowSourceResponse test(UUID id);

    /** Tests an unsaved source draft. */
    WorkflowSourceResponse testDraft(WorkflowSourceRequest request);

    /** Synchronizes the source's remote workflows into the local catalog. */
    List<WorkflowCatalogItemResponse> sync(UUID id);

    /** Returns the catalog items for a source. */
    List<WorkflowCatalogItemResponse> catalogItems(UUID sourceId);

    /** Updates the owner-curated description for one catalog item. */
    WorkflowCatalogItemResponse updateCatalogDescription(UUID itemId, String description);
}
