package io.okagent.module.identity.application;

import java.util.UUID;

/** Authenticated administrator responsible for a security-sensitive change. */
public record AuthenticatedActor(UUID accountId, String username) {}
