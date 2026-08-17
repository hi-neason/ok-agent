package io.okagent.web.user;

public record CreateUserGroupRequest(String groupKey, String name, String description, boolean enabled) {}
