package io.okagent.module.identity.api;

import io.okagent.module.identity.domain.AccountRole;
import io.okagent.module.identity.application.AuthenticatedAccount;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public record AuthUserResponse(
        UUID id, String userId, String username, String displayName, AccountRole role) {
    public static AuthUserResponse from(AuthenticatedAccount account) {
        return new AuthUserResponse(
                account.id(), account.userId(), account.username(), account.displayName(), account.role());
    }

    public static AuthUserResponse from(Jwt jwt) {
        return new AuthUserResponse(
                UUID.fromString(jwt.getClaimAsString("accountId")),
                jwt.getSubject(),
                jwt.getClaimAsString("username"),
                jwt.getClaimAsString("displayName"),
                AccountRole.valueOf(jwt.getClaimAsString("role")));
    }
}
