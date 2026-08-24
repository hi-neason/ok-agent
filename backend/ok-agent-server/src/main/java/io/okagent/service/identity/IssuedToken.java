package io.okagent.service.identity;

/** A signed access token and its lifetime in seconds. */
public record IssuedToken(String value, long expiresIn) {}
