package io.okagent.config;
import static org.assertj.core.api.Assertions.*;
import io.okagent.module.identity.domain.*;
import io.okagent.module.identity.application.JwtTokenService;
import io.okagent.module.identity.infrastructure.persistence.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.transaction.annotation.Transactional;
@SpringBootTest
@Transactional
class SignedTokenRevocationTests {
    @Autowired UserRepository users;
    @Autowired JwtTokenService tokens;
    @Autowired JwtDecoder decoder;
    @Test void actualSignedTokensAreRejectedAfterPasswordReset() {
        String id = UUID.randomUUID().toString();
        var user = new User(UUID.randomUUID(), id, id, "Synthetic", null, null, null, true);
        user.initializeCredentials("synthetic-encoded", AccountRole.ADMIN);
        users.saveAndFlush(user);
        String token = tokens.issue(user).value();
        assertThat(decoder.decode(token).getSubject()).isEqualTo(id);
        user.changePassword("synthetic-new-encoded");
        users.saveAndFlush(user);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        assertThat(decoder.decode(tokens.issue(user).value()).getSubject()).isEqualTo(id);
    }
}
