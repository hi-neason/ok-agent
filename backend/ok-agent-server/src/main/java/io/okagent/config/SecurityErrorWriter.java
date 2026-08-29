package io.okagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.shared.api.Response;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;

final class SecurityErrorWriter {
    private static final ObjectMapper JSON = new ObjectMapper();

    private SecurityErrorWriter() {}

    static void write(
            HttpServletResponse response,
            int status,
            String message,
            String path)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        JSON.writeValue(response.getOutputStream(), Response.error(message, message, path));
    }
}
