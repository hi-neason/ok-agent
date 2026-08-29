package io.okagent.module.identity.application;

/** Result of a successful password login. */
public record LoginResult(String accessToken, long expiresIn, AuthenticatedAccount account) {}
