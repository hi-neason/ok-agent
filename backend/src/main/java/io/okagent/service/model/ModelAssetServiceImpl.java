package io.okagent.service.model;

import io.okagent.domain.model.ModelAsset;
import io.okagent.repository.model.ModelAssetRepository;
import io.okagent.web.model.ModelAssetRequest;
import io.okagent.web.model.ModelAssetResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModelAssetServiceImpl implements ModelAssetService {
  private final ModelAssetRepository repository;

  public ModelAssetServiceImpl(ModelAssetRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ModelAssetResponse> list() {
    return repository.findAll().stream().map(ModelAssetResponse::from).toList();
  }

  @Override
  @Transactional
  public ModelAssetResponse create(ModelAssetRequest request) {
    return ModelAssetResponse.from(
        repository.save(
            new ModelAsset(
                UUID.randomUUID(),
                request.name(),
                request.type(),
                request.provider(),
                request.modelId(),
                request.endpoint(),
                request.secretRef(),
                request.enabled())));
  }

  @Override
  @Transactional
  public ModelAssetResponse update(UUID id, ModelAssetRequest request) {
    var asset = find(id);
    asset.update(
        request.name(),
        request.type(),
        request.provider(),
        request.modelId(),
        request.endpoint(),
        request.secretRef(),
        request.enabled());
    return ModelAssetResponse.from(asset);
  }

  @Override
  @Transactional
  public ModelAssetResponse enabled(UUID id, boolean value) {
    var asset = find(id);
    asset.setEnabled(value);
    return ModelAssetResponse.from(asset);
  }

  @Override
  @Transactional
  public void delete(UUID id) {
    repository.delete(find(id));
  }

  private ModelAsset find(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model asset not found"));
  }
}
