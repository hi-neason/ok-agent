package io.okagent.module.identity.api;

import io.okagent.module.identity.application.*;
import io.okagent.module.identity.application.AuthenticationService;
import io.okagent.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /** Authenticates an enabled console account and returns a signed bearer token. */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(
                LoginResponse.from(authenticationService.login(request.username(), request.password())));
    }

    /** Returns the account identity represented by the current bearer token. */
    @GetMapping("/me")
    public ApiResponse<AuthUserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(AuthUserResponse.from(jwt));
    }
}
