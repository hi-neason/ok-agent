package io.okagent.web.agent;

import io.okagent.service.agent.AgentAssetService;
import io.okagent.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentAssetController {
    private final AgentAssetService service;

    public AgentAssetController(AgentAssetService service) {
        this.service = service;
    }

    /** Returns editable agent drafts in the current management scope. */
    @GetMapping
    public PageResponse<AgentAssetResponse> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.list(page, size));
    }

    /** Returns one editable agent draft by id. */
    @GetMapping("/{id}")
    public AgentAssetResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    /** Creates a new agent draft from basic metadata. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentAssetResponse create(@Valid @RequestBody AgentCreateRequest request) {
        return service.create(request);
    }

    /** Updates the basic metadata (name, description, business domain) of an agent draft. */
    @PutMapping("/{id}")
    public AgentAssetResponse update(@PathVariable UUID id, @Valid @RequestBody AgentUpdateRequest request) {
        return service.update(id, request);
    }

    /** Updates the HarnessAgent configuration (prompt, model, parameters, MCP/skill bindings). */
    @PutMapping("/{id}/configuration")
    public AgentAssetResponse updateConfiguration(
            @PathVariable UUID id, @Valid @RequestBody AgentConfigRequest request) {
        return service.updateConfiguration(id, request);
    }

    /** Validates an agent configuration and returns field-level errors, warnings, and checks. */
    @PostMapping("/{id}/configuration/validate")
    public AgentConfigValidationResponse validateConfiguration(
            @PathVariable UUID id, @RequestBody AgentConfigRequest request) {
        return service.validateConfiguration(id, request);
    }

    /** Enables or disables an agent draft. */
    @PatchMapping("/{id}/enabled")
    public AgentAssetResponse enabled(@PathVariable UUID id, @RequestParam boolean value) {
        return service.setEnabled(id, value);
    }

    /** Deletes an agent draft. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
