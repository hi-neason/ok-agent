package io.okagent.module.model.infrastructure.persistence;

import io.okagent.module.model.domain.ModelAsset;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelAssetRepository extends JpaRepository<ModelAsset, UUID> {}
