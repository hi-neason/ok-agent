package io.okagent.service.skill;

import io.okagent.web.skill.SkillAssetRequest;
import io.okagent.web.skill.SkillAssetResponse;
import java.util.List;
import java.util.UUID;

public interface SkillAssetService {
  /** Returns all reusable skill assets visible to the current management scope. */
  List<SkillAssetResponse> list();

  /** Creates a reusable skill asset from manually entered or imported content. */
  SkillAssetResponse create(SkillAssetRequest request);

  /** Updates the mutable metadata and content of an existing skill asset. */
  SkillAssetResponse update(UUID id, SkillAssetRequest request);

  /** Changes whether a skill asset is available for new Agent references. */
  SkillAssetResponse enabled(UUID id, boolean value);

  /** Permanently removes an unreferenced skill asset from the management plane. */
  void delete(UUID id);
}
