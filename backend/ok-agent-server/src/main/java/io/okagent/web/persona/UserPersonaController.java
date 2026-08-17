package io.okagent.web.persona;

import io.okagent.service.persona.UserPersonaService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/persona")
public class UserPersonaController {

    private final UserPersonaService service;

    public UserPersonaController(UserPersonaService service) {
        this.service = service;
    }

    /** Lists every per-agent persona stored for a user. */
    @GetMapping("/users/{userId}")
    public List<UserPersonaResponse> listForUser(@PathVariable String userId) {
        return service.listForUser(userId);
    }

    /** Returns the persona a specific agent holds for a user (empty shell if none). */
    @GetMapping("/users/{userId}/agents/{agentId}")
    public UserPersonaResponse get(
            @PathVariable String userId, @PathVariable UUID agentId) {
        return service.getOrInit(userId, agentId);
    }

    /** Updates the structured persona fields for a (user, agent). */
    @PutMapping("/users/{userId}/agents/{agentId}")
    public UserPersonaResponse upsert(
            @PathVariable String userId,
            @PathVariable UUID agentId,
            @RequestBody UpsertPersonaRequest request) {
        return service.upsert(userId, agentId, request);
    }

    /** Returns the long-term memory a specific agent holds for a user. */
    @GetMapping("/users/{userId}/agents/{agentId}/memory")
    public Map<String, String> getMemory(
            @PathVariable String userId, @PathVariable UUID agentId) {
        return Map.of("memory", service.readMemory(userId, agentId));
    }

    /** Appends a delta to a (user, agent) long-term memory. */
    @PostMapping("/users/{userId}/agents/{agentId}/memory")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> appendMemory(
            @PathVariable String userId,
            @PathVariable UUID agentId,
            @RequestBody AppendMemoryRequest request) {
        service.appendMemory(userId, agentId, request.delta());
        return Map.of("memory", service.readMemory(userId, agentId));
    }
}
