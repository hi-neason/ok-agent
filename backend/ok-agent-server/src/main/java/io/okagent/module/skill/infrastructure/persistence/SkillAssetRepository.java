package io.okagent.module.skill.infrastructure.persistence;

import io.okagent.module.skill.domain.SkillAsset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillAssetRepository extends JpaRepository<SkillAsset, UUID> {
    Optional<SkillAsset> findBySkillKey(String skillKey);

    boolean existsBySkillKeyAndIdNot(String skillKey, UUID id);
}
