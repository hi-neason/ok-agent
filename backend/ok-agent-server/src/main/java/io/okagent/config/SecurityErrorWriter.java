package io.okagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.shared.api.Response;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
final class SecurityErrorWriter {
    private final ObjectMapper json;

    SecurityErrorWriter(ObjectMapper json) {
        this.json = json;
    }

    void write(
            HttpServletResponse response,
            int status,
            String message,
            String path)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), Response.error(message, message, path));
    }
}
