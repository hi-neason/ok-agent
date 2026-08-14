package io.okagent.web.mcp;

import io.okagent.domain.mcp.McpTransport;
import jakarta.validation.constraints.*;
import java.util.*;

public record McpServerRequest(
    @NotBlank String serverKey,
    @NotBlank String name,
    String description,
    @NotNull McpTransport transport,
    String serverUrl,
    String command,
    List<String> arguments,
    Map<String, String> headers,
    Map<String, String> environment,
    Map<String, String> queryParameters,
    @Min(1) @Max(300) int requestTimeoutSeconds,
    @Min(1) @Max(300) int initializationTimeoutSeconds) {}
