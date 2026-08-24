package io.okagent.service.identity;

import io.okagent.domain.user.User;

public interface JwtTokenService {
    /** Issues a signed access token carrying the account identity and platform role. */
    IssuedToken issue(User user);
}
