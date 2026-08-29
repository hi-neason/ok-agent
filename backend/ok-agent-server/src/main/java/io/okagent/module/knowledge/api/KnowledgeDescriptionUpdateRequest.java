package io.okagent.module.knowledge.api;

import jakarta.validation.constraints.Size;

public record KnowledgeDescriptionUpdateRequest(@Size(max = 4000) String description) {}
