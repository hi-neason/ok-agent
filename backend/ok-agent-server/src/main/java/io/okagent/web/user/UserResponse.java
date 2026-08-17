package io.okagent.web.user;

import io.okagent.domain.user.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String userId,
        String username,
        String displayName,
        String email,
        String phone,
        UUID groupId,
        String groupName,
        boolean enabled,
        Instant updatedAt) {
    public static UserResponse from(User user, String groupName) {
        return new UserResponse(
                user.getId(),
                user.getUserId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhone(),
                user.getGroupId(),
                groupName,
                user.isEnabled(),
                user.getUpdatedAt());
    }
}
