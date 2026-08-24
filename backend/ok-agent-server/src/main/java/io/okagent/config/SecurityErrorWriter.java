package io.okagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;

final class SecurityErrorWriter {
    private static final ObjectMapper JSON = new ObjectMapper();

    private SecurityErrorWriter() {}

    static void write(
            HttpServletResponse response,
            int status,
            String error,
            String message,
            String path)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("path", path);
        JSON.writeValue(response.getOutputStream(), body);
    }
}
