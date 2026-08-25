package io.okagent.service.dialogue;

import io.okagent.domain.dialogue.DialoguePriority;
import io.okagent.domain.dialogue.DialogueWorkStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** Application service for operating customer conversations as assignable inbox work items. */
public interface DialogueWorkItemService {

    /** Lists inbox work items using operational filters and queue ordering. */
    Page<DialogueWorkItemView> list(DialogueWorkItemQuery query, int page, int size);

    /** Returns one work item or rejects a missing session. */
    DialogueWorkItemView get(String sessionId);

    /** Requests human handling and optionally raises the queue priority. */
    DialogueWorkItemView requestHandoff(
            String sessionId, DialoguePriority priority, UUID actorAccountId);

    /** Assigns the current authenticated account and starts active handling. */
    DialogueWorkItemView claim(String sessionId, UUID actorAccountId);

    /** Assigns or unassigns a conversation after validating the target console account. */
    DialogueWorkItemView assign(
            String sessionId, UUID assigneeAccountId, UUID actorAccountId);

    /** Applies an allowed operational lifecycle transition. */
    DialogueWorkItemView transition(
            String sessionId, DialogueWorkStatus status, UUID actorAccountId);

    /** Changes queue priority without changing lifecycle state. */
    DialogueWorkItemView changePriority(
            String sessionId, DialoguePriority priority, UUID actorAccountId);
}
