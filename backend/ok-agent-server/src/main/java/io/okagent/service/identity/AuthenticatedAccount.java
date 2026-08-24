package io.okagent.service.identity;

import io.okagent.domain.user.AccountRole;
import io.okagent.domain.user.User;
import java.util.UUID;

/** Public account identity returned after authentication. */
public record AuthenticatedAccount(
        UUID id, String userId, String username, String displayName, AccountRole role) {
    public static AuthenticatedAccount from(User user) {
        return new AuthenticatedAccount(
                user.getId(), user.getUserId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
