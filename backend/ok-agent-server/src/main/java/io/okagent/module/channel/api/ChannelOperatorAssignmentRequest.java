package io.okagent.module.channel.api;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

/** Complete replacement of the human operators assigned to one channel. */
public record ChannelOperatorAssignmentRequest(@NotNull Set<UUID> operatorAccountIds) {}
