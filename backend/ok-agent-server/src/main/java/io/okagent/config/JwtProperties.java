package io.okagent.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Signing material and lifetime for locally issued access tokens. */
@Validated
@ConfigurationProperties(prefix = "ok-agent.security.jwt")
public record JwtProperties(@NotBlank String secret, @NotNull Duration ttl) {}
