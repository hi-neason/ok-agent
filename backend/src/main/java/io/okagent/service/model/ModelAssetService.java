package io.okagent.service.model;

import io.okagent.web.model.ModelAssetRequest;
import io.okagent.web.model.ModelAssetResponse;
import java.util.List;
import java.util.UUID;

public interface ModelAssetService {
  /** Returns all model assets visible to the current management scope. */
  List<ModelAssetResponse> list();

  /** Creates a reusable model asset from the supplied validated configuration. */
  ModelAssetResponse create(ModelAssetRequest request);

  /** Updates the mutable configuration of the identified model asset. */
  ModelAssetResponse update(UUID id, ModelAssetRequest request);

  /** Changes whether the identified model asset is available for new references. */
  ModelAssetResponse enabled(UUID id, boolean value);

  /** Permanently removes the identified model asset from the management plane. */
  void delete(UUID id);
}
