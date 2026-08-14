package io.okagent.service.skill;

import io.okagent.domain.skill.ArchivedSkillFile;
import java.util.List;

public record ParsedSkillArchive(
    String skillKey,
    String name,
    String description,
    String entryContent,
    String sha256,
    List<ArchivedSkillFile> files) {}
