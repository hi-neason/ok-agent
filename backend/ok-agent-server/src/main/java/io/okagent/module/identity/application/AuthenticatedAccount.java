package io.okagent.module.identity.application;

import io.okagent.module.identity.domain.AccountRole;
import io.okagent.module.identity.domain.User;
import java.util.UUID;

/** Public account identity returned after authentication. */
public record AuthenticatedAccount(
        UUID id, String userId, String username, String displayName, AccountRole role) {
    public static AuthenticatedAccount from(User user) {
        return new AuthenticatedAccount(
                user.getId(), user.getUserId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
