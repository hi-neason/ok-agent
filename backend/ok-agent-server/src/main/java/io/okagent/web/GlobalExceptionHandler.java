package io.okagent.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Translates our business-level {@link ResponseStatusException}s into a stable, semantic JSON
 * error body. Spring Boot's default error view omits the exception reason unless
 * {@code server.error.include-message} is globally enabled (which would also leak internal
 * messages from unexpected exceptions), so we handle the expected case explicitly here and keep
 * the field shape ({@code timestamp/status/error/message/path}) consistent.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body(baseBody(status, ex.getReason(), request));
    }

    /**
     * Bean Validation failures on {@code @Valid @RequestBody} (e.g. missing required fields).
     * Surface the first field-level message so the UI gets a human-readable reason instead of an
     * empty 400.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .findFirst()
                .orElse("请求参数校验失败");
        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, message, request);
        return ResponseEntity.badRequest().body(body);
    }

    private static String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }

    private static Map<String, Object> baseBody(HttpStatus status, String message, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message != null ? message : status.getReasonPhrase());
        body.put("path", request.getRequestURI());
        return body;
    }
}
