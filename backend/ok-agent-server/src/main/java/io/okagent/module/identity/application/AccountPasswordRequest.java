package io.okagent.module.identity.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountPasswordRequest(@NotBlank @Size(min = 12, max = 256) String password) {}
