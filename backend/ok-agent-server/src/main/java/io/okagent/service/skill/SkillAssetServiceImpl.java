package io.okagent.service.skill;

import io.okagent.domain.skill.SkillAsset;
import io.okagent.repository.skill.SkillAssetRepository;
import io.okagent.web.skill.SkillAssetRequest;
import io.okagent.web.skill.SkillAssetResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SkillAssetServiceImpl implements SkillAssetService {
  private final SkillAssetRepository repository;

  public SkillAssetServiceImpl(SkillAssetRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<SkillAssetResponse> list() {
    return repository.findAll().stream().map(SkillAssetResponse::from).toList();
  }

  @Override
  @Transactional
  public SkillAssetResponse create(SkillAssetRequest request) {
    ensureUniqueKey(request.skillKey(), null);
    return SkillAssetResponse.from(
        repository.save(
            new SkillAsset(
                UUID.randomUUID(),
                request.skillKey(),
                request.name(),
                request.description(),
                request.assetVersion(),
                request.sourceType(),
                normalizeOptional(request.sourceUri()),
                request.entryFile(),
                request.content(),
                request.enabled())));
  }

  @Override
  @Transactional
  public SkillAssetResponse update(UUID id, SkillAssetRequest request) {
    var asset = find(id);
    ensureUniqueKey(request.skillKey(), id);
    asset.update(
        request.skillKey(),
        request.name(),
        request.description(),
        request.assetVersion(),
        request.sourceType(),
        normalizeOptional(request.sourceUri()),
        request.entryFile(),
        request.content(),
        request.enabled());
    return SkillAssetResponse.from(asset);
  }

  @Override
  @Transactional
  public SkillAssetResponse enabled(UUID id, boolean value) {
    var asset = find(id);
    asset.setEnabled(value);
    return SkillAssetResponse.from(asset);
  }

  @Override
  @Transactional
  public void delete(UUID id) {
    repository.delete(find(id));
  }

  private SkillAsset find(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill asset not found"));
  }

  private void ensureUniqueKey(String skillKey, UUID currentId) {
    var exists =
        currentId == null
            ? repository.existsBySkillKey(skillKey)
            : repository.existsBySkillKeyAndIdNot(skillKey, currentId);
    if (exists) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Skill key already exists");
    }
  }

  private String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
