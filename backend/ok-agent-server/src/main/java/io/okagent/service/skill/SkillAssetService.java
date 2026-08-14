package io.okagent.service.skill;

import io.okagent.web.skill.SkillAssetResponse;
import io.okagent.web.skill.SkillFileContentResponse;
import io.okagent.web.skill.SkillFileResponse;
import io.okagent.web.skill.SkillMetadataRequest;
import java.util.List;
import java.util.UUID;

public interface SkillAssetService {
  /** Returns all reusable skill assets visible to the current management scope. */
  List<SkillAssetResponse> list();

  /** Imports and parses a complete Skill ZIP archive, optionally replacing a duplicate Skill. */
  SkillAssetResponse importArchive(
      String archiveName,
      byte[] archive,
      String requestedName,
      String requestedDescription,
      String businessDomain,
      boolean overwrite);

  /** Updates the user-editable metadata of an imported Skill without changing its files. */
  SkillAssetResponse updateMetadata(UUID id, SkillMetadataRequest request);

  /** Lists all files stored for an imported Skill in stable path order. */
  List<SkillFileResponse> listFiles(UUID id);

  /** Returns preview content for one file in an imported Skill. */
  SkillFileContentResponse getFile(UUID id, String path);

  /** Changes whether a skill asset is available for new Agent references. */
  SkillAssetResponse enabled(UUID id, boolean value);

  /** Permanently removes an unreferenced skill asset from the management plane. */
  void delete(UUID id);
}
