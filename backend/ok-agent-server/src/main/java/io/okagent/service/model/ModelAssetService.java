package io.okagent.service.model;

import io.okagent.web.model.ModelAssetRequest;
import io.okagent.web.model.ModelAssetResponse;
import io.okagent.web.model.ModelConnectionTestResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface ModelAssetService {
    /** Returns model assets paginated by most-recently-updated, visible to the current scope. */
    Page<ModelAssetResponse> list(int page, int size);

    /** Creates a reusable model asset from the supplied validated configuration. */
    ModelAssetResponse create(ModelAssetRequest request);

    /** Updates the mutable configuration of the identified model asset. */
    ModelAssetResponse update(UUID id, ModelAssetRequest request);

    /** Changes whether the identified model asset is available for new references. */
    ModelAssetResponse enabled(UUID id, boolean value);

    /** Permanently removes the identified model asset from the management plane. */
    void delete(UUID id);

    /** Sends one real minimal request to the provider using the saved model configuration. */
    ModelConnectionTestResponse testConnection(UUID id);

    /** Sends one real minimal request using an unsaved model configuration. */
    ModelConnectionTestResponse testConnection(ModelAssetRequest request);
}
