package io.okagent.service.identity;

import io.okagent.domain.user.AccountRole;
import io.okagent.domain.user.User;
import io.okagent.domain.user.UserSource;
import io.okagent.repository.user.UserRepository;
import io.okagent.web.identity.AccountCreateRequest;
import io.okagent.web.identity.AccountPasswordRequest;
import io.okagent.web.identity.AccountResponse;
import io.okagent.web.identity.AccountUpdateRequest;
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

    public AccountServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
    public AccountResponse create(AccountCreateRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "USERNAME_CONFLICT");
        }
        User user = new User(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                username,
                request.displayName().trim(),
                null,
                null,
                null,
                request.enabled());
        user.initializeCredentials(passwordEncoder.encode(request.password()), request.role());
        return AccountResponse.from(userRepository.save(user));
    }

    @Override
    @Transactional
    public AccountResponse update(UUID id, UUID actorId, AccountUpdateRequest request) {
        User user = findInteractiveAccount(id);
        boolean removesAdmin = user.getRole() == AccountRole.ADMIN
                && (request.role() != AccountRole.ADMIN || !request.enabled());
        if (removesAdmin && id.equals(actorId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CANNOT_CHANGE_OWN_ADMIN_ACCESS");
        }
        if (removesAdmin
                && userRepository.countBySourceAndRoleAndEnabledTrue(UserSource.CONSOLE, AccountRole.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "LAST_ADMIN_REQUIRED");
        }
        user.updateAccountAccess(request.displayName().trim(), request.role(), request.enabled());
        return AccountResponse.from(userRepository.save(user));
    }

    @Override
    @Transactional
    public void changePassword(UUID id, AccountPasswordRequest request) {
        User user = findInteractiveAccount(id);
        user.changePassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);
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
