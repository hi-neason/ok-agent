package io.okagent.web.identity;

import io.okagent.domain.user.AccountRole;
import io.okagent.domain.user.User;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String username,
        String displayName,
        AccountRole role,
        boolean enabled,
        Instant lastLoginAt,
        Instant updatedAt) {
    public static AccountResponse from(User user) {
        return new AccountResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.isEnabled(),
                user.getLastLoginAt(),
                user.getUpdatedAt());
    }
}
