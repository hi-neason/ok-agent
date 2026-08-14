package io.okagent.web.skill;

public record SkillFileContentResponse(
    String path, String mediaType, long size, boolean previewable, String content) {}
