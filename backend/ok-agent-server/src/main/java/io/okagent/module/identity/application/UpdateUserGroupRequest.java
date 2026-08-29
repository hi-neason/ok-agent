package io.okagent.module.identity.application;

public record UpdateUserGroupRequest(String groupKey, String name, String description, boolean enabled) {}
