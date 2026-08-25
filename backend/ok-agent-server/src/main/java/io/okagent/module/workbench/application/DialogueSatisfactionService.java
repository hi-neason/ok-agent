package io.okagent.module.workbench.application;

import java.util.UUID;

/** Captures and retrieves customer satisfaction for completed conversations. */
public interface DialogueSatisfactionService {
    /** Returns the satisfaction feedback associated with one conversation. */
    DialogueSatisfactionView get(String sessionId);

    /** Saves a satisfaction score and optional customer feedback. */
    DialogueSatisfactionView save(String sessionId, int rating, String feedback, UUID actorAccountId);
}
