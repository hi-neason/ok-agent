package io.okagent.repository.model;

import io.okagent.domain.model.ModelAsset;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelAssetRepository extends JpaRepository<ModelAsset, UUID> {}
