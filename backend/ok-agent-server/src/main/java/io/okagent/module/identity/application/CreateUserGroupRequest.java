package io.okagent.module.identity.application;

public record CreateUserGroupRequest(String groupKey, String name, String description, boolean enabled) {}
