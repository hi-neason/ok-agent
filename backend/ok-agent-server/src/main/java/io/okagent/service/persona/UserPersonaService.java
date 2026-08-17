package io.okagent.service.persona;

import io.okagent.web.persona.UpsertPersonaRequest;
import io.okagent.web.persona.UserPersonaResponse;
import java.util.List;
import java.util.UUID;

public interface UserPersonaService {

    /**
     * Returns the persona that {@code agentId} holds for {@code userId} (empty shell when none).
     */
    UserPersonaResponse getOrInit(String userId, UUID agentId);

    /**
     * Returns all per-agent personas stored for {@code userId} (one entry per agent that has
     * extracted or stored one). Used by the management UI and for GLOBAL injection merges.
     */
    List<UserPersonaResponse> listForUser(String userId);

    /** Replaces the structured persona fields for a (user, agent) (null fields are left unchanged). */
    UserPersonaResponse upsert(String userId, UUID agentId, UpsertPersonaRequest request);

    /** Reads the free-form long-term memory a specific agent holds for a user. */
    String readMemory(String userId, UUID agentId);

    /** Appends a delta to a (user, agent) long-term memory, stamped with the current time. */
    void appendMemory(String userId, UUID agentId, String delta);

    /**
     * Renders the persona block to inject for {@code agentId}.
     *
     * @param mode SELF_ONLY renders only this agent's persona; GLOBAL merges across all agents for
     *     the user; NONE yields an empty string.
     * @param template optional {@code {summary}/{tags}/{preferences}/{facts}/{memory}} template.
     */
    String getProfileBlock(String userId, UUID agentId, String mode, String template);
}
