package io.okagent.module.agent.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentCreateRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 1024) String description,
        @NotBlank @Size(max = 64) String businessDomain) {}
