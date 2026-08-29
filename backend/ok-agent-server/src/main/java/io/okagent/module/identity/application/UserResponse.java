package io.okagent.module.identity.application;

import io.okagent.module.identity.domain.AccountRole;
import io.okagent.module.identity.domain.User;
import io.okagent.module.identity.domain.UserSource;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String userId,
        String username,
        AccountRole role,
        String displayName,
        UserSource source,
        String avatarUrl,
        String email,
        String phone,
        UUID groupId,
        String groupName,
        boolean enabled,
        int channelCount,
        Instant updatedAt) {
    public static UserResponse from(User user, String groupName) {
        return from(user, groupName, 0);
    }

    public static UserResponse from(User user, String groupName, int channelCount) {
        return new UserResponse(
                user.getId(),
                user.getUserId(),
                user.getUsername(),
                user.getRole(),
                user.getDisplayName(),
                user.getSource(),
                user.getAvatarUrl(),
                user.getEmail(),
                user.getPhone(),
                user.getGroupId(),
                groupName,
                user.isEnabled(),
                channelCount,
                user.getUpdatedAt());
    }
}
