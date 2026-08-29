package io.okagent.module.identity.application;

import io.okagent.module.identity.domain.AccountRole;
import io.okagent.module.identity.domain.User;
import io.okagent.module.identity.domain.UserSource;
import io.okagent.module.identity.infrastructure.persistence.UserRepository;
import io.okagent.module.identity.api.AccountCreateRequest;
import io.okagent.module.identity.api.AccountPasswordRequest;
import io.okagent.module.identity.api.AccountResponse;
import io.okagent.module.identity.api.AccountUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountServiceImpl implements AccountService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService auditService;

    public AccountServiceImpl(
            UserRepository userRepository, PasswordEncoder passwordEncoder, SecurityAuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountResponse> list(int page, int size) {
        return userRepository
                .findBySourceAndPasswordHashIsNotNull(
                        UserSource.CONSOLE,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .map(AccountResponse::from);
    }

    @Override
    @Transactional
    public AccountResponse create(AuthenticatedActor actor, AccountCreateRequest request) {
        String username = request.username().trim();
        User user = userRepository.findByUsername(username).map(existing -> {
            if (existing.getSource() != UserSource.CONSOLE || existing.hasCredentials()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "USERNAME_CONFLICT");
            }
            existing.update(
                    username,
                    request.displayName().trim(),
                    existing.getEmail(),
                    existing.getPhone(),
                    existing.getGroupId(),
                    request.enabled());
            return existing;
        }).orElseGet(() -> new User(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                username,
                request.displayName().trim(),
                null,
                null,
                null,
                request.enabled()));
        user.initializeCredentials(passwordEncoder.encode(request.password()), request.role());
        User saved = userRepository.save(user);
        auditService.record(
                actor,
                "ACCOUNT_CREATED",
                "ACCOUNT",
                saved.getId().toString(),
                "role=" + saved.getRole() + ";enabled=" + saved.isEnabled());
        return AccountResponse.from(saved);
    }

    @Override
    @Transactional
    public AccountResponse update(UUID id, AuthenticatedActor actor, AccountUpdateRequest request) {
        User user = findInteractiveAccount(id);
        AccountRole previousRole = user.getRole();
        boolean previouslyEnabled = user.isEnabled();
        boolean removesAdmin = user.getRole() == AccountRole.ADMIN
                && (request.role() != AccountRole.ADMIN || !request.enabled());
        if (removesAdmin && id.equals(actor.accountId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CANNOT_CHANGE_OWN_ADMIN_ACCESS");
        }
        if (removesAdmin
                && userRepository.countBySourceAndRoleAndEnabledTrue(UserSource.CONSOLE, AccountRole.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "LAST_ADMIN_REQUIRED");
        }
        user.updateAccountAccess(request.displayName().trim(), request.role(), request.enabled());
        User saved = userRepository.save(user);
        auditService.record(
                actor,
                "ACCOUNT_ACCESS_UPDATED",
                "ACCOUNT",
                saved.getId().toString(),
                "role=" + previousRole + "->" + saved.getRole() + ";enabled=" + previouslyEnabled + "->"
                        + saved.isEnabled());
        return AccountResponse.from(saved);
    }

    @Override
    @Transactional
    public void changePassword(UUID id, AuthenticatedActor actor, AccountPasswordRequest request) {
        User user = findInteractiveAccount(id);
        user.changePassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        auditService.record(actor, "ACCOUNT_PASSWORD_RESET", "ACCOUNT", user.getId().toString(), "");
    }

    private User findInteractiveAccount(UUID id) {
        User user = userRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"));
        if (user.getSource() != UserSource.CONSOLE || !user.hasCredentials()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
        }
        return user;
    }
}
