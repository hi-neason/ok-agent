package io.okagent.repository.skill;

import io.okagent.domain.skill.SkillAsset;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillAssetRepository extends JpaRepository<SkillAsset, UUID> {
  boolean existsBySkillKey(String skillKey);

  boolean existsBySkillKeyAndIdNot(String skillKey, UUID id);
}
