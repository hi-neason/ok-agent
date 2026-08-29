package io.okagent.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import io.okagent.module.identity.application.UserConflictException;
import io.okagent.module.identity.application.UserNotFoundException;
import io.okagent.module.model.application.ApiKeyProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Translates our business-level {@link ResponseStatusException}s into a stable, semantic JSON
 * error body. Spring Boot's default error view omits the exception reason unless
 * {@code server.error.include-message} is globally enabled (which would also leak internal
 * messages from unexpected exceptions), so expected failures are translated into
 * {@link Response} without exposing unexpected exception details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Response<Void>> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Response.error(
                "USER_NOT_FOUND", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(UserConflictException.class)
    public ResponseEntity<Response<Void>> handleUserConflict(
            UserConflictException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Response.error(
                "USER_CONFLICT", ex.getMessage(), request.getRequestURI()));
    }

    /** Returns an actionable response when persisted credentials were encrypted with another key. */
    @ExceptionHandler(ApiKeyProcessingException.class)
    public ResponseEntity<Response<Void>> handleApiKeyProcessing(
            ApiKeyProcessingException ex, HttpServletRequest request) {
        return ResponseEntity.unprocessableEntity().body(Response.error(
                "CREDENTIAL_DECRYPTION_FAILED",
                "模型 API Key 无法解密，请到模型管理中重新填写并保存 API Key",
                request.getRequestURI()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Response<Void>> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body(Response.error(
                "HTTP_" + status.value(),
                ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(),
                request.getRequestURI()));
    }

    /**
     * Bean Validation failures on {@code @Valid @RequestBody} (e.g. missing required fields).
     * Surface the first field-level message so the UI gets a human-readable reason instead of an
     * empty 400.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<Void>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .findFirst()
                .orElse("请求参数校验失败");
        return ResponseEntity.badRequest().body(Response.error(
                "VALIDATION_ERROR", message, request.getRequestURI()));
    }

    /** Returns a stable client error when the JSON request body cannot be decoded. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Response<Void>> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(Response.error(
                "MALFORMED_REQUEST", "请求体格式不正确", request.getRequestURI()));
    }

    /** Hides unexpected implementation details while retaining the failure in server logs. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled API failure path={}", request.getRequestURI(), ex);
        return ResponseEntity.internalServerError().body(Response.error(
                "INTERNAL_ERROR", "服务暂时不可用", request.getRequestURI()));
    }

    private static String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }

}
