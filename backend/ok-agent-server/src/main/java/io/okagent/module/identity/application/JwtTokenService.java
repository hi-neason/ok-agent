package io.okagent.module.identity.application;

import io.okagent.module.identity.domain.User;

public interface JwtTokenService {
    /** Issues a signed access token carrying the account identity and platform role. */
    IssuedToken issue(User user);
}
