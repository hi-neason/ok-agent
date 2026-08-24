package io.okagent.service.dialogue;

import io.okagent.domain.dialogue.DialogueSession;
import io.okagent.domain.dialogue.DialogueTurn;
import java.util.List;
import java.util.Optional;
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

    /** Rejects access when an existing session belongs to another agent or user. */
    void assertSessionOwner(String sessionId, UUID agentId, String userId);

    /** Creates the session if absent, otherwise returns the existing one. */
    DialogueSession ensureSession(String sessionId, UUID agentId, String userId, String title);

    /**
     * Creates a production session attributed to a specific release/version. Default implementation
     * ignores release info; production callers use the implementation that persists it for
     * per-version observability.
     */
    default DialogueSession ensureSession(
            String sessionId, UUID agentId, UUID releaseId, Integer versionNo, String userId, String title) {
        return ensureSession(sessionId, agentId, userId, title);
    }

    /** Appends one exchange to a session, assigning the next sequence number. */
    DialogueTurn recordMessage(String sessionId, String role, String content, String model, Integer latencyMs);

    /** Returns the sequence number the next recorded turn would receive (1-based). */
    int nextSeq(String sessionId);

    /**
     * Appends one exchange to a session and links it to an execution trace. The trace id is stored
     * on assistant turns so the observability UI can expand the turn into its span tree.
     */
    DialogueTurn recordMessage(
            String sessionId, String role, String content, String model, Integer latencyMs, String traceId);

    /** Refreshes a session's last-activity timestamp. */
    void touchSession(String sessionId);

    /** Removes the session and all its turns. Harness state/transcript are not touched here. */
    void purge(String sessionId);

    /** Returns the turns of a session in chronological order. */
    List<DialogueTurn> getMessages(String sessionId);

    /** Returns the session with the given id, if it exists. */
    Optional<DialogueSession> findById(String sessionId);

    /** Lists sessions for one agent, newest activity first. */
    List<DialogueSession> findByAgentId(UUID agentId);

    /** Cross-agent, optionally filtered, paginated search for the observability list view. */
    Page<DialogueSummary> search(DialogueQuery query, int page, int size);
}
