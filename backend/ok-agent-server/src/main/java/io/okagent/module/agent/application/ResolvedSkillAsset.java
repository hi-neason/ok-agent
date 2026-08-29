package io.okagent.module.agent.application;

/** Frozen Skill manifest content consumed by a released Agent. */
public record ResolvedSkillAsset(String skillKey, String description, String content) {}
