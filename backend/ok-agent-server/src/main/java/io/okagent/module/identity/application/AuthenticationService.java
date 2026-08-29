package io.okagent.module.identity.application;

public interface AuthenticationService {
    /** Verifies local credentials and returns a signed access token for the enabled account. */
    LoginResult login(String username, String password);
}
