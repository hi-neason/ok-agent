package io.okagent.web.knowledge;

import jakarta.validation.constraints.Size;

public record KnowledgeDescriptionUpdateRequest(@Size(max = 4000) String description) {}
