package io.okagent.service.skill;

import io.okagent.web.skill.SkillAssetResponse;
import io.okagent.web.skill.SkillFileContentResponse;
import io.okagent.web.skill.SkillFileResponse;
import io.okagent.web.skill.SkillFileUpdateRequest;
import io.okagent.web.skill.SkillMetadataRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface SkillAssetService {
    /** Returns reusable skill assets paginated by most-recently-updated. */
    Page<SkillAssetResponse> list(int page, int size);

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

    /** Updates one editable UTF-8 text file with optimistic concurrency protection. */
    SkillFileContentResponse updateFile(UUID id, SkillFileUpdateRequest request);

    /** Changes whether a skill asset is available for new Agent references. */
    SkillAssetResponse enabled(UUID id, boolean value);

    /** Permanently removes an unreferenced skill asset from the management plane. */
    void delete(UUID id);
}
