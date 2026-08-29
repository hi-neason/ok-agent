package io.okagent.module.identity.api;

public record UpdateUserGroupRequest(String groupKey, String name, String description, boolean enabled) {}
