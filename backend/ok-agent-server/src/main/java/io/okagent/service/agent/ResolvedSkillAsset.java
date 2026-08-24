package io.okagent.service.agent;

/** Frozen Skill manifest content consumed by a released Agent. */
public record ResolvedSkillAsset(String skillKey, String description, String content) {}
