package io.okagent.module.agentmanager.api;

import jakarta.validation.constraints.Size;

/** Request body for creating a new immutable agent version from the current draft. */
public record CreateVersionRequest(
        @Size(max = 128, message = "version label must be at most 128 characters") String label,
        @Size(max = 4000, message = "changelog must be at most 4000 characters") String changelog) {}
