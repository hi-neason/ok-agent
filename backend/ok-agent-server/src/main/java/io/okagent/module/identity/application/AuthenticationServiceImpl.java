package io.okagent.module.identity.application;

import io.okagent.module.identity.domain.User;
import io.okagent.module.identity.domain.UserSource;
import io.okagent.module.identity.infrastructure.persistence.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthenticationServiceImpl implements AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final String dummyPasswordHash;

    public AuthenticationServiceImpl(
            UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public LoginResult login(String username, String password) {
        Optional<User> eligible = userRepository.findByUsername(username.trim())
                .filter(user -> user.getSource() == UserSource.CONSOLE)
                .filter(User::isEnabled)
                .filter(User::hasCredentials);
        String passwordHash = eligible.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(password, passwordHash);
        if (eligible.isEmpty() || !passwordMatches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        User user = eligible.orElseThrow();
        user.recordSuccessfulLogin();
        userRepository.save(user);
        IssuedToken token = tokenService.issue(user);
        return new LoginResult(token.value(), token.expiresIn(), AuthenticatedAccount.from(user));
    }
}
