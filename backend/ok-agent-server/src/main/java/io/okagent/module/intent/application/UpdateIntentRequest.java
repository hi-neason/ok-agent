package io.okagent.module.intent.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record UpdateIntentRequest(
        @NotBlank @Size(max = 255) String name,
        UUID parentId,
        @Size(max = 4000) String description,
        @Size(max = 100) List<@Size(max = 1000) String> examples,
        int sortOrder) {}
