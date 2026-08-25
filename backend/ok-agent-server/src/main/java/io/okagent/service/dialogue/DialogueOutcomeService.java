package io.okagent.service.dialogue;

import java.util.UUID;

/** Maintains the structured business result associated with a conversation. */
public interface DialogueOutcomeService {
    DialogueOutcomeView get(String sessionId);

    DialogueOutcomeView save(String sessionId, DialogueOutcomeDraft draft, UUID actorAccountId);
}
