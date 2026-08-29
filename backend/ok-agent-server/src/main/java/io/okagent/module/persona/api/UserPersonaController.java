package io.okagent.module.persona.api;

import io.okagent.module.persona.application.*;

import io.okagent.module.agent.domain.AgentAsset;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.persona.infrastructure.persistence.UserPersonaRepository;
import io.okagent.module.persona.application.UserPersonaService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/persona")
public class UserPersonaController {

    private final UserPersonaService service;
    private final AgentAssetRepository agents;
    private final UserPersonaRepository personas;

    public UserPersonaController(
            UserPersonaService service, AgentAssetRepository agents, UserPersonaRepository personas) {
        this.service = service;
        this.agents = agents;
        this.personas = personas;
    }

    /** Coverage map: userId -> list of agentIds that hold a persona for that user. */
    @GetMapping("/coverage")
    public Map<String, List<UUID>> coverage() {
        return personas.findCoverage().stream()
                .collect(Collectors.groupingBy(
                        UserPersonaRepository.PersonaCoverageRow::getUserId,
                        LinkedHashMap::new,
                        Collectors.mapping(UserPersonaRepository.PersonaCoverageRow::getAgentId, Collectors.toList())));
    }

    /** Lists every per-agent persona stored for a user. */
    @GetMapping("/users/{userId}")
    public List<UserPersonaResponse> listForUser(@PathVariable String userId) {
        return service.listForUser(userId);
    }

    /**
     * Renders the exact persona block that will be injected into the given agent's system prompt for
     * the user, using that agent's configured injection mode and template. Returns an empty block when
     * injection is disabled or there is no persona data. Used by the management UI as a WYSIWYG
     * preview and as the single source of truth for injection behavior.
     */
    @GetMapping("/users/{userId}/agents/{agentId}/injection-preview")
    public Map<String, String> injectionPreview(@PathVariable String userId, @PathVariable UUID agentId) {
        AgentAsset agent = agents.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "agent not found"));
        String mode = agent.getPersonaInjectionMode() == null
                ? "NONE"
                : agent.getPersonaInjectionMode().name();
        String block = service.getProfileBlock(userId, agentId, mode, agent.getPersonaPromptTemplate());
        return Map.of("mode", mode, "block", block == null ? "" : block.strip());
    }

    /** Returns the persona a specific agent holds for a user (empty shell if none). */
    @GetMapping("/users/{userId}/agents/{agentId}")
    public UserPersonaResponse get(@PathVariable String userId, @PathVariable UUID agentId) {
        return service.getOrInit(userId, agentId);
    }

    /** Updates the structured persona fields for a (user, agent). */
    @PutMapping("/users/{userId}/agents/{agentId}")
    public UserPersonaResponse upsert(
            @PathVariable String userId, @PathVariable UUID agentId, @RequestBody UpsertPersonaRequest request) {
        return service.upsert(userId, agentId, request);
    }

    /** Returns the long-term memory a specific agent holds for a user. */
    @GetMapping("/users/{userId}/agents/{agentId}/memory")
    public Map<String, String> getMemory(@PathVariable String userId, @PathVariable UUID agentId) {
        return Map.of("memory", service.readMemory(userId, agentId));
    }

    /** Appends a delta to a (user, agent) long-term memory. */
    @PostMapping("/users/{userId}/agents/{agentId}/memory")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> appendMemory(
            @PathVariable String userId, @PathVariable UUID agentId, @RequestBody AppendMemoryRequest request) {
        service.appendMemory(userId, agentId, request.delta());
        return Map.of("memory", service.readMemory(userId, agentId));
    }
}
