package io.okagent.module.persona.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppendMemoryRequest(@NotBlank @Size(max = 20000) String delta) {}
