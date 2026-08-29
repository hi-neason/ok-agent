package io.okagent.module.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountPasswordRequest(@NotBlank @Size(min = 12, max = 256) String password) {}
