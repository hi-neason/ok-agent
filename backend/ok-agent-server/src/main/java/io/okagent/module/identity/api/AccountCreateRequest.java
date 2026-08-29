package io.okagent.module.identity.api;

import io.okagent.module.identity.domain.AccountRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AccountCreateRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(min = 12, max = 256) String password,
        @NotNull AccountRole role,
        boolean enabled) {}
