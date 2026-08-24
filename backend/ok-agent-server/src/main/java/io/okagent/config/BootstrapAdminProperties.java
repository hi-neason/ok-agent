package io.okagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Optional first-run administrator credentials supplied by the deployment environment. */
@ConfigurationProperties(prefix = "ok-agent.security.bootstrap-admin")
public record BootstrapAdminProperties(String username, String password) {
    public BootstrapAdminProperties {
        username = username == null || username.isBlank() ? "admin" : username.trim();
        password = password == null ? "" : password;
    }
}
