package io.okagent.module.persona.application;

import java.util.List;
import java.util.Map;

public record UpsertPersonaRequest(List<String> tags, Map<String, String> preferences, String facts, String summary) {}
