package io.okagent.module.identity.application;

/** A signed access token and its lifetime in seconds. */
public record IssuedToken(String value, long expiresIn) {}
