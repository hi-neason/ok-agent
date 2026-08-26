package io.okagent.module.workbench.api;

import io.okagent.domain.channel.OperatorPresenceStatus;
import java.time.Instant;

/** Current availability of the authenticated operator. */
public record OperatorPresenceResponse(OperatorPresenceStatus status, Instant updatedAt) {}
