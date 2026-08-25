package io.okagent.module.customerchat.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.okagent.module.customerchat.application.CustomerChatCommand;
import java.util.UUID;

/** Production chat entry (intent-routed). Unlike the debug endpoint, the caller does not pick an
 *  agent — the router agent is chosen by {@code agentId} and the sub-agent by the detected intent. */
public record CustomerChatRequest(
        @NotNull(message = "agentId is required") UUID agentId,
        @Size(max = 128, message = "channelId must not exceed 128 characters") String channelId,
        @Size(max = 128, message = "sessionId must not exceed 128 characters") String sessionId,
        @NotBlank(message = "userId is required") @Size(max = 128) String userId,
        @NotBlank(message = "message is required") @Size(max = 20000) String message) {
    CustomerChatCommand toCommand() {
        return new CustomerChatCommand(agentId, channelId, sessionId, userId, message);
    }
}
