package io.okagent.module.identity.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateUserRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
        @NotBlank @Size(max = 128) String displayName,
        @Email @Size(max = 320) String email,
        @Size(max = 32) String phone,
        UUID groupId,
        boolean enabled) {}
