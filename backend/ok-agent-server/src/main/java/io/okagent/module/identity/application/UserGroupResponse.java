package io.okagent.module.identity.application;

import io.okagent.module.identity.domain.UserGroup;
import java.time.Instant;
import java.util.UUID;

public record UserGroupResponse(
        UUID id, String groupKey, String name, String description, boolean enabled, long userCount, Instant updatedAt) {
    public static UserGroupResponse from(UserGroup group, long userCount) {
        return new UserGroupResponse(
                group.getId(),
                group.getGroupKey(),
                group.getName(),
                group.getDescription(),
                group.isEnabled(),
                userCount,
                group.getUpdatedAt());
    }
}
