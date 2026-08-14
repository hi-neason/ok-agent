package io.okagent.repository.skill;

import io.okagent.domain.skill.SkillFile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillFileRepository extends JpaRepository<SkillFile, UUID> {
  List<SkillFile> findAllBySkillIdOrderByFilePath(UUID skillId);

  Optional<SkillFile> findBySkillIdAndFilePath(UUID skillId, String filePath);
}
