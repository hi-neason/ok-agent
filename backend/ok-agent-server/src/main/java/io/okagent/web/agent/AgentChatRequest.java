package io.okagent.web.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentChatRequest(@NotBlank @Size(max = 20000) String message, @Size(max = 64) String sessionId) {}
