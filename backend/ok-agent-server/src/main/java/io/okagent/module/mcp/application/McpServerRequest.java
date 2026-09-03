package io.okagent.module.mcp.application;

import io.okagent.module.mcp.domain.McpTransport;
import jakarta.validation.constraints.*;
import java.util.*;

public record McpServerRequest(
        @NotBlank @Size(max = 128) String serverKey,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 4000) String description,
        @NotNull McpTransport transport,
        @Size(max = 2048) String serverUrl,
        @Size(max = 1024) String command,
        @Size(max = 100) List<@Size(max = 4096) String> arguments,
        Map<String, String> headers,
        Map<String, String> environment,
        Map<String, String> queryParameters,
        @Min(1) @Max(300) int requestTimeoutSeconds,
        @Min(1) @Max(300) int initializationTimeoutSeconds) {}
