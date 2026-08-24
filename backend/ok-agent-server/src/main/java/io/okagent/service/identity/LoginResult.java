package io.okagent.service.identity;

/** Result of a successful password login. */
public record LoginResult(String accessToken, long expiresIn, AuthenticatedAccount account) {}
