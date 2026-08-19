package io.okagent.service.channel.runtime;

import java.util.UUID;

/**
 * Internal event fired after an Agent's configuration is committed, so channels bound to that
 * agent can rebuild their live HarnessAgent with the new settings.
 */
public record AgentConfigChangedEvent(UUID agentId) {}
