package io.okagent.service.release;

import io.okagent.service.agent.ResolvedAgentConfig;
import java.util.UUID;

/** Immutable runtime identity and configuration resolved from a channel's promoted release. */
public record ReleasedChannelAgent(UUID agentId, String agentKey, String agentName, ResolvedAgentConfig config) {}
