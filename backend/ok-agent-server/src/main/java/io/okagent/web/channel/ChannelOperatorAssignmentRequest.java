package io.okagent.web.channel;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

/** Complete replacement of the human operators assigned to one channel. */
public record ChannelOperatorAssignmentRequest(@NotNull Set<UUID> operatorAccountIds) {}
