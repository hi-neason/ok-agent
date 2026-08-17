package io.okagent.web.persona;

import io.okagent.service.persona.UserPersonaService;
import java.util.Map;
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

    /** Returns the persona for a user (empty shell if none stored yet). */
    @GetMapping("/{userId}")
    public UserPersonaResponse get(@PathVariable String userId) {
        return service.getOrInit(userId);
    }

    /** Updates the structured persona fields for a user. */
    @PutMapping("/{userId}")
    public UserPersonaResponse upsert(
            @PathVariable String userId, @RequestBody UpsertPersonaRequest request) {
        return service.upsert(userId, request);
    }

    /** Returns the user's free-form long-term memory (MEMORY.md). */
    @GetMapping("/{userId}/memory")
    public Map<String, String> getMemory(@PathVariable String userId) {
        return Map.of("memory", service.readMemory(userId));
    }

    /** Appends a delta to the user's long-term memory. */
    @PostMapping("/{userId}/memory")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> appendMemory(
            @PathVariable String userId, @RequestBody AppendMemoryRequest request) {
        service.appendMemory(userId, request.delta());
        return Map.of("memory", service.readMemory(userId));
    }
}
