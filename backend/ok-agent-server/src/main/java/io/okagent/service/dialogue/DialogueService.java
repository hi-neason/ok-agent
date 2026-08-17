package io.okagent.service.dialogue;

import io.okagent.domain.dialogue.DialogueSession;
import io.okagent.domain.dialogue.DialogueTurn;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

/**
 * Shared, runtime-agnostic persistence for conversation history. Every producer (the debug runtime
 * today, real runtime instances later) records through this service, and read surfaces such as the
 * observability module query through it. Harness-level working memory ({@code agent_state}) and the
 * event transcript ({@code agent_transcript}) are intentionally out of scope here.
 */
public interface DialogueService {

    /** True when a session with the given id already exists. */
    boolean sessionExists(String sessionId);

    /** Creates the session if absent, otherwise returns the existing one. */
    DialogueSession ensureSession(String sessionId, UUID agentId, String userId, String title);

    /** Appends one exchange to a session, assigning the next sequence number. */
    void recordMessage(String sessionId, String role, String content, String model, Integer latencyMs);

    /** Refreshes a session's last-activity timestamp. */
    void touchSession(String sessionId);

    /** Removes the session and all its turns. Harness state/transcript are not touched here. */
    void purge(String sessionId);

    /** Returns the turns of a session in chronological order. */
    List<DialogueTurn> getMessages(String sessionId);

    /** Lists sessions for one agent, newest activity first. */
    List<DialogueSession> findByAgentId(UUID agentId);

    /** Cross-agent, optionally filtered, paginated search for the observability list view. */
    Page<DialogueSummary> search(DialogueQuery query, int page, int size);
}
