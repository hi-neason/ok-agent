package io.okagent.service.persona;

import io.okagent.web.persona.UpsertPersonaRequest;
import io.okagent.web.persona.UserPersonaResponse;
import java.util.Map;

public interface UserPersonaService {

    /**
     * Returns the persona for a user, or an empty shell (no stored structured fields) when none
     * exists yet. The free-form MEMORY.md content is included when present.
     */
    UserPersonaResponse getOrInit(String userId);

    /** Replaces the structured persona fields (null fields are left unchanged). */
    UserPersonaResponse upsert(String userId, UpsertPersonaRequest request);

    /** Reads the free-form long-term memory (MEMORY.md) for a user. */
    String readMemory(String userId);

    /** Appends a delta to the user's long-term memory, stamped with the current time. */
    void appendMemory(String userId, String delta);

    /**
     * Renders the persona as a text block for injection into an agent's system prompt. When
     * {@code template} is non-blank, simple {@code {summary}/{tags}/{preferences}/{facts}/{memory}}
     * placeholders are substituted; otherwise a default format is used. Returns empty string when the
     * user has neither structured persona nor memory.
     */
    String getProfileBlock(String userId, String template);
}
