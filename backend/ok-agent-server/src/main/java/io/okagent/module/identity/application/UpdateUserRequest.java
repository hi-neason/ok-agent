package io.okagent.module.identity.application;

import java.util.UUID;

public record UpdateUserRequest(
        String username, String displayName, String email, String phone, UUID groupId, boolean enabled) {}
