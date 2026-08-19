package io.okagent.service.dialogue;

import java.util.UUID;

/** Optional filters for {@link DialogueService#search}. An omitted (null) filter is not applied. */
public record DialogueQuery(String sessionId, String userId, UUID agentId, String from, String to) {}
