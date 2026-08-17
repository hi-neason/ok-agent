package io.okagent.web.user;

import java.util.UUID;

public record UpdateUserRequest(
        String username, String displayName, String email, String phone, UUID groupId, boolean enabled) {}
