package io.okagent.web.identity;

import io.okagent.service.identity.LoginResult;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, AuthUserResponse user) {
    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                result.accessToken(), "Bearer", result.expiresIn(), AuthUserResponse.from(result.account()));
    }
}
