package io.okagent.module.mcp.application;

import io.okagent.module.mcp.domain.*;
import java.time.Instant;
import java.util.*;

public record McpServerResponse(
        UUID id,
        String serverKey,
        String name,
        String description,
        McpTransport transport,
        String serverUrl,
        String command,
        List<String> arguments,
        Map<String, String> queryParameters,
        Set<String> configuredHeaderNames,
        Set<String> configuredEnvironmentNames,
        boolean enabled,
        int requestTimeoutSeconds,
        int initializationTimeoutSeconds,
        String lastTestStatus,
        Instant lastTestedAt,
        int toolCount,
        Instant updatedAt) {}
