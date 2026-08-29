package io.okagent.module.model.application;

import io.okagent.module.model.domain.ModelAsset;
import io.okagent.module.model.infrastructure.persistence.ModelAssetRepository;
import io.okagent.module.model.application.ModelAssetRequest;
import io.okagent.module.model.application.ModelAssetResponse;
import io.okagent.module.model.application.ModelConnectionTestResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModelAssetServiceImpl implements ModelAssetService {
    private final ModelAssetRepository repository;
    private final ApiKeyCipher apiKeyCipher;
    private final ModelConnectionTester connectionTester;

    public ModelAssetServiceImpl(
            ModelAssetRepository repository, ApiKeyCipher apiKeyCipher, ModelConnectionTester connectionTester) {
        this.repository = repository;
        this.apiKeyCipher = apiKeyCipher;
        this.connectionTester = connectionTester;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ModelAssetResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return repository.findAll(pageable).map(ModelAssetResponse::from);
    }

    @Override
    @Transactional
    public ModelAssetResponse create(ModelAssetRequest request) {
        return ModelAssetResponse.from(repository.save(new ModelAsset(
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

    @Override
    public ModelConnectionTestResponse testConnection(ModelAssetRequest request) {
        var apiKey = requiredApiKey(request.apiKey());
        var transientAsset = new ModelAsset(
                UUID.randomUUID(),
                request.name(),
                request.type(),
                request.provider(),
                request.modelId(),
                request.endpoint(),
                apiKeyCipher.encrypt(apiKey),
                request.enabled());
        return connectionTester.test(transientAsset, apiKey);
    }

    private String requiredApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API key is required for a new model asset");
        return apiKey;
    }

    private ModelAsset find(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model asset not found"));
    }
}
