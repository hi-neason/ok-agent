package io.okagent.module.workbench.api;

import io.okagent.module.channel.domain.OperatorPresenceStatus;
import jakarta.validation.constraints.NotNull;

/** Updates the authenticated operator's availability. */
public record OperatorPresenceRequest(@NotNull OperatorPresenceStatus status) {}
