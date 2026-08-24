package io.okagent.web.identity;

import io.okagent.service.identity.AccountService;
import io.okagent.service.identity.AuthenticatedActor;
import io.okagent.web.observe.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** Lists interactive console accounts for administrator management. */
    @GetMapping
    public PageResponse<AccountResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(accountService.list(page, size));
    }

    /** Creates an interactive console account with a platform role. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AccountCreateRequest request) {
        return accountService.create(actor(jwt), request);
    }

    /** Updates an interactive account's display name, role, and enabled state. */
    @PutMapping("/{id}")
    public AccountResponse update(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AccountUpdateRequest request) {
        return accountService.update(id, actor(jwt), request);
    }

    /** Replaces an interactive account password without returning credential material. */
    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AccountPasswordRequest request) {
        accountService.changePassword(id, actor(jwt), request);
    }

    private static AuthenticatedActor actor(Jwt jwt) {
        return new AuthenticatedActor(
                UUID.fromString(jwt.getClaimAsString("accountId")), jwt.getClaimAsString("username"));
    }
}
