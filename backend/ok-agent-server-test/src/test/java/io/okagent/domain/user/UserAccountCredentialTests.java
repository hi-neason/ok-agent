package io.okagent.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAccountCredentialTests {
    @Test
    void initializesConsoleCredentialsOnlyOnce() {
        User user = consoleUser();

        user.initializeCredentials("bcrypt-hash", AccountRole.ADMIN);

        assertThat(user.hasCredentials()).isTrue();
        assertThat(user.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(user.getRole()).isEqualTo(AccountRole.ADMIN);
        assertThatThrownBy(() -> user.initializeCredentials("replacement", AccountRole.VIEWER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ACCOUNT_CREDENTIALS_ALREADY_INITIALIZED");
    }

    @Test
    void rejectsCredentialsForChannelIdentity() {
        User user =
                User.forChannel(UUID.randomUUID(), UUID.randomUUID().toString(), "channel-user", "User", null);

        assertThatThrownBy(() -> user.initializeCredentials("bcrypt-hash", AccountRole.ADMIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CHANNEL_USER_CANNOT_SIGN_IN");
    }

    private static User consoleUser() {
        return new User(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                "admin",
                "Administrator",
                null,
                null,
                null,
                true);
    }
}
