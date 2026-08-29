package io.okagent.module.skill.application;

import io.okagent.module.skill.domain.SkillFile;

public record SkillFileResponse(String path, String mediaType, long size) {
    public static SkillFileResponse from(SkillFile file) {
        return new SkillFileResponse(file.getFilePath(), file.getMediaType(), file.getFileSize());
    }
}
