package io.okagent.module.persona.application;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record UpsertPersonaRequest(
        @Size(max = 100) List<@Size(max = 128) String> tags,
        @Size(max = 100) Map<@Size(max = 128) String, @Size(max = 1000) String> preferences,
        @Size(max = 20000) String facts,
        @Size(max = 8000) String summary) {}
