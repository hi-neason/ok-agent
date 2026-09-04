package io.okagent.module.mcp.application;

import io.okagent.module.mcp.domain.McpTransport;
import jakarta.validation.constraints.*;
import java.util.*;

public record McpServerRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._-]+") String serverKey,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 4000) String description,
        @NotNull McpTransport transport,
        @Size(max = 2048) String serverUrl,
        @Size(max = 1024) String command,
        @Size(max = 100) List<@NotBlank @Size(max = 4096) String> arguments,
        @Size(max = 100) Map<@Size(max = 256) String, @Size(max = 4096) String> headers,
        @Size(max = 100) Map<@Size(max = 256) String, @Size(max = 4096) String> environment,
        @Size(max = 100) Map<@Size(max = 256) String, @Size(max = 4096) String> queryParameters,
        @Min(1) @Max(300) int requestTimeoutSeconds,
        @Min(1) @Max(300) int initializationTimeoutSeconds) {}
