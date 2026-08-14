package io.okagent.web.skill;

import io.okagent.domain.skill.SkillFile;

public record SkillFileResponse(String path, String mediaType, long size) {
  public static SkillFileResponse from(SkillFile file) {
    return new SkillFileResponse(file.getFilePath(), file.getMediaType(), file.getFileSize());
  }
}
