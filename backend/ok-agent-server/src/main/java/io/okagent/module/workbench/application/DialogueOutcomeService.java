package io.okagent.module.workbench.application;

import java.util.UUID;

/** Maintains the structured business result associated with a conversation. */
public interface DialogueOutcomeService {
    /** Returns the structured business outcome for one conversation. */
    DialogueOutcomeView get(String sessionId);

    /** Saves the structured business outcome and records the acting account. */
    DialogueOutcomeView save(String sessionId, DialogueOutcomeDraft draft, UUID actorAccountId);
}
