package io.okagent.web.persona;

import java.util.List;
import java.util.Map;

public record UpsertPersonaRequest(List<String> tags, Map<String, String> preferences, String facts, String summary) {}
