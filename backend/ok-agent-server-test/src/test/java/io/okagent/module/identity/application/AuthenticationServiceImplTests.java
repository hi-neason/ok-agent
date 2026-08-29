package io.okagent.module.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.okagent.module.identity.domain.AccountRole;
import io.okagent.module.identity.domain.User;
import io.okagent.module.identity.infrastructure.persistence.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AuthenticationServiceImplTests {
    private UserRepository userRepository;
    private JwtTokenService tokenService;
    private PasswordEncoder passwordEncoder;
    private AuthenticationServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenService = mock(JwtTokenService.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        service = new AuthenticationServiceImpl(userRepository, passwordEncoder, tokenService);
    }

    @Test
    void authenticatesEnabledConsoleAccount() {
        User user = consoleUser(true);
        user.initializeCredentials(passwordEncoder.encode("correct-password"), AccountRole.ADMIN);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(tokenService.issue(user)).thenReturn(new IssuedToken("signed-token", 3600));

        LoginResult result = service.login(" admin ", "correct-password");

        assertThat(result.accessToken()).isEqualTo("signed-token");
        assertThat(result.account().role()).isEqualTo(AccountRole.ADMIN);
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void rejectsUnknownAccountAndWrongPasswordIdentically() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());
        User user = consoleUser(true);
        user.initializeCredentials(passwordEncoder.encode("correct-password"), AccountRole.VIEWER);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        assertInvalidCredentials(() -> service.login("missing", "wrong-password"));
        assertInvalidCredentials(() -> service.login("admin", "wrong-password"));
    }

    @Test
    void rejectsDisabledAccount() {
        User user = consoleUser(false);
        user.initializeCredentials(passwordEncoder.encode("correct-password"), AccountRole.ADMIN);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        assertInvalidCredentials(() -> service.login("admin", "correct-password"));
    }

    private static void assertInvalidCredentials(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_CREDENTIALS");
    }

    private static User consoleUser(boolean enabled) {
        return new User(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                "admin",
                "Administrator",
                null,
                null,
                null,
                enabled);
    }
}
