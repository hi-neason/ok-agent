package io.okagent.module.identity.api;

import io.okagent.module.identity.domain.AccountRole;
import io.okagent.module.identity.domain.User;
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
