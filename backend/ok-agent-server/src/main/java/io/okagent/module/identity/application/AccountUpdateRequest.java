package io.okagent.module.identity.application;

import io.okagent.module.identity.domain.AccountRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountUpdateRequest(
        @NotBlank @Size(max = 128) String displayName,
        @NotNull AccountRole role,
        boolean enabled) {}
