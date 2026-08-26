package io.okagent.module.workbench.api;

import io.okagent.domain.channel.ChannelRuntimeStatus;
import io.okagent.domain.channel.ChannelType;
import java.time.Instant;
import java.util.UUID;

/** Channel account visible to the authenticated human operator. */
public record MyChannelResponse(
        UUID id,
        String name,
        ChannelType type,
        ChannelRuntimeStatus runtimeStatus,
        boolean enabled,
        UUID boundAgentId,
        String boundAgentName,
        long customerCount,
        long operatorCount,
        Instant assignedAt) {}
