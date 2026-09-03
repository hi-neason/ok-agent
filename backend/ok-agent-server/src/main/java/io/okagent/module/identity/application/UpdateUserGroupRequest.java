package io.okagent.module.identity.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserGroupRequest(
        @NotBlank @Size(max = 64) String groupKey,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 1024) String description,
        boolean enabled) {}
