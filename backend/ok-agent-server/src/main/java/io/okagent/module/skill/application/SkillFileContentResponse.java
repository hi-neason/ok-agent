package io.okagent.module.skill.application;

public record SkillFileContentResponse(
        String path,
        String mediaType,
        long size,
        boolean previewable,
        String content,
        long version,
        java.time.Instant updatedAt) {}
