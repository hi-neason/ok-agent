package io.okagent.module.identity.api;

import io.okagent.module.identity.application.LoginResult;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, AuthUserResponse user) {
    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                result.accessToken(), "Bearer", result.expiresIn(), AuthUserResponse.from(result.account()));
    }
}
