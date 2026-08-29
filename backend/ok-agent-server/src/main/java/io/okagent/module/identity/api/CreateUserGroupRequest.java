package io.okagent.module.identity.api;

public record CreateUserGroupRequest(String groupKey, String name, String description, boolean enabled) {}
