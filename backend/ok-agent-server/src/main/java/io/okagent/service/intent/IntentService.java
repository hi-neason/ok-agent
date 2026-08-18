package io.okagent.service.intent;

import java.util.List;
import java.util.UUID;

public interface IntentService {
    /** Returns the full intent tree (roots first, each node with its children). */
    List<IntentNode> getTree();

    /** Returns a single intent by id. */
    IntentDto get(UUID id);

    /** Creates a new intent node. */
    IntentDto create(CreateIntentRequest request);

    /** Updates an existing intent's definition. */
    IntentDto update(UUID id, UpdateIntentRequest request);

    /** Deletes an intent. Rejects deletion when it still has children. */
    void delete(UUID id);
}
