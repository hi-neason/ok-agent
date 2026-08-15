package io.okagent.service.model;

import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.model.transport.OkHttpTransport;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared HTTP transport for outbound model calls.
 *
 * <p>Uses OkHttp with HTTP/1.1 instead of the JDK HttpClient default (which negotiates HTTP/2). Some
 * OpenAI-compatible gateways reset JDK HTTP/2 connections mid-request (surfacing as {@code
 * java.net.ConnectException: ClosedChannelException}), while OkHttp over HTTP/1.1 works reliably. A
 * single transport is reused across model instances so its connection pool is shared.
 */
@Configuration
public class ModelTransportConfig {

    private HttpTransport transport;

    @Bean
    public HttpTransport modelHttpTransport() {
        var config = io.agentscope.core.model.transport.HttpTransportConfig.builder()
                .httpVersion(io.agentscope.core.model.transport.HttpVersion.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofMinutes(2))
                .readTimeout(Duration.ofMinutes(2))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
        transport = OkHttpTransport.builder().config(config).build();
        return transport;
    }

    @PreDestroy
    public void shutdown() {
        if (transport != null) {
            try {
                transport.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }
}
