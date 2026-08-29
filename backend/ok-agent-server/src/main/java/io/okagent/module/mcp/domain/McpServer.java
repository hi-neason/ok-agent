package io.okagent.module.mcp.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mcp_server")
public class McpServer {
    @Id
    private UUID id;

    @Column(name = "server_key", nullable = false, unique = true)
    private String serverKey;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private McpTransport transport;

    @Column(name = "server_url")
    private String serverUrl;

    @Column(name = "command_text")
    private String command;

    @Column(name = "arguments_json", nullable = false, columnDefinition = "TEXT")
    private String argumentsJson;

    @Column(name = "query_parameters_json", nullable = false, columnDefinition = "TEXT")
    private String queryParametersJson;

    @Column(name = "secrets_ciphertext", columnDefinition = "TEXT")
    private String secretsCiphertext;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "request_timeout_seconds", nullable = false)
    private int requestTimeoutSeconds;

    @Column(name = "initialization_timeout_seconds", nullable = false)
    private int initializationTimeoutSeconds;

    @Column(name = "last_test_status", nullable = false)
    private String lastTestStatus;

    @Column(name = "last_tested_at")
    private Instant lastTestedAt;

    @Column(name = "tool_count", nullable = false)
    private int toolCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected McpServer() {}

    public McpServer(
            UUID id,
            String serverKey,
            String name,
            String description,
            McpTransport transport,
            String serverUrl,
            String command,
            String argumentsJson,
            String queryParametersJson,
            String secretsCiphertext,
            int requestTimeoutSeconds,
            int initializationTimeoutSeconds) {
        this.id = id;
        this.createdAt = Instant.now();
        this.lastTestStatus = "UNTESTED";
        this.enabled = true;
        this.toolCount = 0;
        update(
                serverKey,
                name,
                description,
                transport,
                serverUrl,
                command,
                argumentsJson,
                queryParametersJson,
                secretsCiphertext,
                requestTimeoutSeconds,
                initializationTimeoutSeconds);
    }

    public void update(
            String serverKey,
            String name,
            String description,
            McpTransport transport,
            String serverUrl,
            String command,
            String argumentsJson,
            String queryParametersJson,
            String secretsCiphertext,
            int requestTimeoutSeconds,
            int initializationTimeoutSeconds) {
        this.serverKey = serverKey;
        this.name = name;
        this.description = description;
        this.transport = transport;
        this.serverUrl = serverUrl;
        this.command = command;
        this.argumentsJson = argumentsJson;
        this.queryParametersJson = queryParametersJson;
        if (secretsCiphertext != null && !secretsCiphertext.isBlank()) this.secretsCiphertext = secretsCiphertext;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.initializationTimeoutSeconds = initializationTimeoutSeconds;
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public void recordTest(boolean success, int toolCount) {
        this.lastTestStatus = success ? "SUCCESS" : "FAILED";
        this.lastTestedAt = Instant.now();
        if (success) this.toolCount = toolCount;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getServerKey() {
        return serverKey;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public McpTransport getTransport() {
        return transport;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getCommand() {
        return command;
    }

    public String getArgumentsJson() {
        return argumentsJson;
    }

    public String getQueryParametersJson() {
        return queryParametersJson;
    }

    public String getSecretsCiphertext() {
        return secretsCiphertext;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public int getInitializationTimeoutSeconds() {
        return initializationTimeoutSeconds;
    }

    public String getLastTestStatus() {
        return lastTestStatus;
    }

    public Instant getLastTestedAt() {
        return lastTestedAt;
    }

    public int getToolCount() {
        return toolCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
