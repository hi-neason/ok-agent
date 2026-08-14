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
@Service public class ModelAssetService {
 private final ModelAssetRepository repository; public ModelAssetService(ModelAssetRepository repository){this.repository=repository;}
 @Transactional(readOnly=true) public List<ModelAssetResponse> list(){return repository.findAll().stream().map(ModelAssetResponse::from).toList();}
 @Transactional public ModelAssetResponse create(ModelAssetRequest r){return ModelAssetResponse.from(repository.save(new ModelAsset(UUID.randomUUID(),r.name(),r.type(),r.provider(),r.modelId(),r.endpoint(),r.secretRef(),r.enabled())));}
 @Transactional public ModelAssetResponse update(UUID id,ModelAssetRequest r){var a=find(id);a.update(r.name(),r.type(),r.provider(),r.modelId(),r.endpoint(),r.secretRef(),r.enabled());return ModelAssetResponse.from(a);}
 @Transactional public ModelAssetResponse enabled(UUID id,boolean value){var a=find(id);a.setEnabled(value);return ModelAssetResponse.from(a);}
 @Transactional public void delete(UUID id){repository.delete(find(id));}
 private ModelAsset find(UUID id){return repository.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Model asset not found"));}
}
