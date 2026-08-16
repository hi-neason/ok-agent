package io.okagent.service.agent;

import io.okagent.web.agent.AgentAssetResponse;
import io.okagent.web.agent.AgentConfigRequest;
import io.okagent.web.agent.AgentConfigValidationResponse;
import io.okagent.web.agent.AgentCreateRequest;
import io.okagent.web.agent.AgentUpdateRequest;
import java.util.List;
import java.util.UUID;

public interface AgentAssetService {
    /** Returns all agent drafts in the current management scope, newest first. */
    List<AgentAssetResponse> list();

    /** Returns one agent draft by id. */
    AgentAssetResponse get(UUID id);

    /** Creates a new agent draft from basic metadata. */
    AgentAssetResponse create(AgentCreateRequest request);

    /** Updates the basic metadata (name, description, business domain) of an agent draft. */
    AgentAssetResponse update(UUID id, AgentUpdateRequest request);

    /** Updates the HarnessAgent configuration (prompt, model, parameters, bindings). */
    AgentAssetResponse updateConfiguration(UUID id, AgentConfigRequest request);

    /** Enables or disables an agent draft. */
    AgentAssetResponse setEnabled(UUID id, boolean enabled);

    /** Deletes an agent draft. */
    void delete(UUID id);

    /**
     * Validates an agent configuration without persisting it.
     *
     * <p>Resolves referenced models, MCP servers, and skills; checks MCP tool allowlists against the
     * latest tool snapshots; enforces memory/workspace, docker, isolation-scope, permission, and
     * context-budget constraints. Returns a structured result with field-level errors, warnings, and
     * per-area checks so the UI can group issues by configuration tab.
     *
     * @param id the agent draft being validated
     * @param request the candidate configuration
     * @return a non-mutating validation result (always 200, never throws for an invalid payload)
     */
    AgentConfigValidationResponse validateConfiguration(UUID id, AgentConfigRequest request);
}
