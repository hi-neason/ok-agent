package io.okagent.module.workbench.application;

import io.okagent.module.channel.domain.ChannelRuntimeStatus;
import io.okagent.module.channel.domain.ChannelType;
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
