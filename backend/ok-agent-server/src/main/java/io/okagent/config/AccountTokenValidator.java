package io.okagent.config;

import io.okagent.module.identity.infrastructure.persistence.UserRepository;
import io.okagent.module.identity.domain.UserSource;
import java.util.UUID;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Validates current account access on every signed-token request, across all server replicas. */
@Component
public class AccountTokenValidator implements OAuth2TokenValidator<Jwt> {
    private final UserRepository users;
    public AccountTokenValidator(UserRepository users) { this.users = users; }
    @Override public OAuth2TokenValidatorResult validate(Jwt token) {
        try {
            Number securityVersion = token.getClaim("securityVersion");
            var account = users.findById(UUID.fromString(token.getClaimAsString("accountId"))).orElse(null);
            if (account != null && account.isEnabled() && account.hasCredentials()
                    && account.getSource() == UserSource.CONSOLE && securityVersion != null
                    && account.getSecurityVersion() == securityVersion.longValue()
                    && account.getRole().name().equals(token.getClaimAsString("role"))
                    && account.getUserId().equals(token.getSubject())) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (IllegalArgumentException | ClassCastException exception) {
            // Malformed identity claims must fail authentication without exposing account details.
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Account access has changed", null));
    }
}
