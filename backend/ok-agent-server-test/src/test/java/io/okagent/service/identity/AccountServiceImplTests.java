package io.okagent.service.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.okagent.domain.user.AccountRole;
import io.okagent.domain.user.User;
import io.okagent.domain.user.UserSource;
import io.okagent.repository.user.UserRepository;
import io.okagent.web.identity.AccountCreateRequest;
import io.okagent.web.identity.AccountUpdateRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AccountServiceImplTests {
    private UserRepository repository;
    private SecurityAuditService auditService;
    private AccountServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        auditService = mock(SecurityAuditService.class);
        service = new AccountServiceImpl(repository, new BCryptPasswordEncoder(4), auditService);
    }

    @Test
    void createsPasswordBackedAccount() {
        AccountCreateRequest request =
                new AccountCreateRequest("editor", "Editor", "strong-password", AccountRole.EDITOR, true);
        when(repository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthenticatedActor actor = actor();
        var result = service.create(actor, request);

        assertThat(result.username()).isEqualTo("editor");
        assertThat(result.role()).isEqualTo(AccountRole.EDITOR);
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(User::hasCredentials));
        verify(auditService)
                .record(
                        org.mockito.ArgumentMatchers.eq(actor),
                        org.mockito.ArgumentMatchers.eq("ACCOUNT_CREATED"),
                        org.mockito.ArgumentMatchers.eq("ACCOUNT"),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.notNull());
    }

    @Test
    void preventsAdministratorFromDemotingItself() {
        User admin = account(AccountRole.ADMIN, true);
        when(repository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertConflict(() -> service.update(
                admin.getId(),
                new AuthenticatedActor(admin.getId(), "admin"),
                new AccountUpdateRequest("Admin", AccountRole.VIEWER, true)));
    }

    @Test
    void preservesLastEnabledAdministrator() {
        User admin = account(AccountRole.ADMIN, true);
        when(repository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(repository.countBySourceAndRoleAndEnabledTrue(UserSource.CONSOLE, AccountRole.ADMIN))
                .thenReturn(1L);

        assertConflict(() -> service.update(
                admin.getId(), actor(), new AccountUpdateRequest("Admin", AccountRole.ADMIN, false)));
    }

    private static void assertConflict(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    private static User account(AccountRole role, boolean enabled) {
        User user = new User(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                "admin",
                "Admin",
                null,
                null,
                null,
                enabled);
        user.initializeCredentials("bcrypt-hash", role);
        return user;
    }

    private static AuthenticatedActor actor() {
        return new AuthenticatedActor(UUID.randomUUID(), "operator");
    }
}
