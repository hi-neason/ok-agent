package io.okagent.module.workbench.application;

import io.okagent.module.channel.domain.OperatorPresenceStatus;
import java.time.Instant;

/** Current availability of the authenticated operator. */
public record OperatorPresenceResponse(OperatorPresenceStatus status, Instant updatedAt) {}
