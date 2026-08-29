package io.okagent.module.knowledge.application;

import io.okagent.module.knowledge.application.KnowledgeCatalogItemResponse;
import io.okagent.module.knowledge.application.KnowledgeSourceRequest;
import io.okagent.module.knowledge.application.KnowledgeSourceResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** CRUD, connection testing, and catalog synchronization for external knowledge sources. */
public interface KnowledgeSourceService {

    /** Lists knowledge sources paginated by most-recently-updated. */
    Page<KnowledgeSourceResponse> list(int page, int size);

    /** Returns one source by id. */
    KnowledgeSourceResponse get(UUID id);

    /** Creates a new source (does not test or sync automatically). */
    KnowledgeSourceResponse create(KnowledgeSourceRequest request);

    /** Updates an existing source; API key is only changed when a non-blank value is supplied. */
    KnowledgeSourceResponse update(UUID id, KnowledgeSourceRequest request);

    /** Enables or disables a source. */
    KnowledgeSourceResponse setEnabled(UUID id, boolean enabled);

    /** Deletes a source and its catalog items (cascaded). */
    void delete(UUID id);

    /** Tests a saved source and records the result. */
    KnowledgeSourceResponse test(UUID id);

    /** Tests an unsaved source draft. */
    KnowledgeSourceResponse testDraft(KnowledgeSourceRequest request);

    /** Synchronizes the source's remote knowledge bases into the local catalog. */
    List<KnowledgeCatalogItemResponse> sync(UUID id);

    /** Returns the catalog items for a source. */
    List<KnowledgeCatalogItemResponse> catalogItems(UUID sourceId);

    /** Updates the owner-curated description for one catalog item. */
    KnowledgeCatalogItemResponse updateCatalogDescription(UUID itemId, String description);
}
