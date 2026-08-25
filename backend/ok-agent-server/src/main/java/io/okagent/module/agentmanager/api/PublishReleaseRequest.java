package io.okagent.module.agentmanager.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request body for publishing an agent version onto a channel. */
public record PublishReleaseRequest(
        @NotNull(message = "versionNo is required") Integer versionNo,
        @NotNull(message = "channelId is required") UUID channelId) {}
