package io.okagent.module.identity.application;

import io.okagent.config.BootstrapAdminProperties;
import io.okagent.module.identity.domain.AccountRole;
import io.okagent.module.identity.domain.User;
import io.okagent.module.identity.infrastructure.persistence.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Initializes the first built-in administrator from deployment-only credentials. */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);
    private static final int MINIMUM_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties properties;

    public BootstrapAdminRunner(
            UserRepository userRepository, PasswordEncoder passwordEncoder, BootstrapAdminProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.password().isBlank()) {
            log.warn(
                    "Bootstrap administrator is not configured; set OK_AGENT_BOOTSTRAP_ADMIN_PASSWORD before enabling authentication");
            return;
        }
        if (properties.password().length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalStateException("Bootstrap administrator password must contain at least 12 characters");
        }

        User user = userRepository.findByUsername(properties.username()).orElseGet(() -> new User(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                properties.username(),
                properties.username(),
                null,
                null,
                null,
                true));
        if (user.hasCredentials()) {
            log.info("Bootstrap administrator credentials are already initialized for username={}", properties.username());
            return;
        }

        user.initializeCredentials(passwordEncoder.encode(properties.password()), AccountRole.ADMIN);
        userRepository.save(user);
        log.info("Bootstrap administrator initialized for username={}", properties.username());
    }
}
