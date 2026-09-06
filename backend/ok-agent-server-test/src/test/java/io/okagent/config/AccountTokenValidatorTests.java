package io.okagent.config;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.okagent.module.identity.domain.*;
import io.okagent.module.identity.infrastructure.persistence.UserRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
class AccountTokenValidatorTests {
    @Test void invalidatesExistingTokensOnDisableRoleChangeAndPasswordReset() {
        var repository = mock(UserRepository.class);
        var account = new User(UUID.randomUUID(), "u", "user", "User", null, null, null, true);
        account.initializeCredentials("encoded", AccountRole.ADMIN);
        when(repository.findById(account.getId())).thenReturn(Optional.of(account));
        var validator = new AccountTokenValidator(repository);
        var token = token(account);
        assertThat(validator.validate(token).hasErrors()).isFalse();
        account.updateAccountAccess("User", AccountRole.ADMIN, false);
        assertThat(validator.validate(token).hasErrors()).isTrue();
        account.updateAccountAccess("User", AccountRole.ADMIN, true);
        assertThat(validator.validate(token).hasErrors()).isTrue();
        var renewed = token(account);
        account.updateAccountAccess("User", AccountRole.VIEWER, true);
        assertThat(validator.validate(renewed).hasErrors()).isTrue();
        var viewer = token(account);
        account.changePassword("new-encoded");
        assertThat(validator.validate(viewer).hasErrors()).isTrue();
        assertThat(validator.validate(token(account)).hasErrors()).isFalse();
    }
    private Jwt token(User user) {
        return Jwt.withTokenValue("signed").header("alg", "HS256").subject(user.getUserId())
                .claim("accountId", user.getId().toString()).claim("role", user.getRole().name())
                .claim("securityVersion", user.getSecurityVersion()).build();
    }
}
