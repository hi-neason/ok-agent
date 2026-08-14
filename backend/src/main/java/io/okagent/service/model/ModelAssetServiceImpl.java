package io.okagent.service.model;

import io.okagent.domain.model.ModelAsset;
import io.okagent.repository.model.ModelAssetRepository;
import io.okagent.web.model.ModelAssetRequest;
import io.okagent.web.model.ModelAssetResponse;
import io.okagent.web.model.ModelConnectionTestResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModelAssetServiceImpl implements ModelAssetService {
  private final ModelAssetRepository repository;
  private final ApiKeyCipher apiKeyCipher;
  private final ModelConnectionTester connectionTester;

  public ModelAssetServiceImpl(ModelAssetRepository repository, ApiKeyCipher apiKeyCipher, ModelConnectionTester connectionTester) {
    this.repository = repository;
    this.apiKeyCipher = apiKeyCipher;
    this.connectionTester = connectionTester;
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
                apiKeyCipher.encrypt(requiredApiKey(request.apiKey())),
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
        request.apiKey() == null || request.apiKey().isBlank() ? null : apiKeyCipher.encrypt(request.apiKey()),
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

  @Override
  @Transactional(readOnly = true)
  public ModelConnectionTestResponse testConnection(UUID id) {
    var asset = find(id);
    return connectionTester.test(asset, apiKeyCipher.decrypt(asset.getApiKeyCiphertext()));
  }

  private String requiredApiKey(String apiKey) {
    if (apiKey == null || apiKey.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API key is required for a new model asset");
    return apiKey;
  }

  private ModelAsset find(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model asset not found"));
  }
}
