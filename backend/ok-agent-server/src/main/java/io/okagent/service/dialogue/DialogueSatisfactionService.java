package io.okagent.service.dialogue;

import java.util.UUID;

/** Captures and retrieves customer satisfaction for completed conversations. */
public interface DialogueSatisfactionService {
    DialogueSatisfactionView get(String sessionId);

    DialogueSatisfactionView save(String sessionId, int rating, String feedback, UUID actorAccountId);
}
