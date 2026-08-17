package io.okagent.web.user;

public record UpdateUserGroupRequest(String groupKey, String name, String description, boolean enabled) {}
