package io.okagent.module.agent.api;

import io.okagent.module.agent.application.*;
import io.okagent.module.agent.application.AgentDebugService;
import io.okagent.shared.api.Response;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentDebugController {
    private final AgentDebugService service;

    public AgentDebugController(AgentDebugService service) {
        this.service = service;
    }

    /** Sends one message to a debug session and returns the HarnessAgent reply. */
    @PostMapping("/{id}/chat")
    public Response<AgentChatResponse> chat(@PathVariable UUID id, @Valid @RequestBody AgentChatRequest request) {
        return Response.success(service.chat(id, request));
    }

    /** Closes and discards a debug session so the next chat starts a fresh conversation. */
    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Response<Void> resetSession(@PathVariable String sessionId, @RequestParam String userId) {
        service.resetSession(sessionId, userId);
        return Response.success(null);
    }
}
