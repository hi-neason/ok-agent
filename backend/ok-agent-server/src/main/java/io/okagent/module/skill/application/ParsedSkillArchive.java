package io.okagent.module.skill.application;

import io.okagent.module.skill.domain.ArchivedSkillFile;
import java.util.List;

public record ParsedSkillArchive(
        String skillKey,
        String name,
        String description,
        String entryContent,
        String sha256,
        List<ArchivedSkillFile> files) {}
