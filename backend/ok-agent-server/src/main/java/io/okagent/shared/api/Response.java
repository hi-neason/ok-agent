package io.okagent.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Stable envelope returned by every JSON API endpoint. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record Response<T>(boolean success, String code, String message, T data, String timestamp, String path) {

    /** Creates a successful API response. */
    public static <T> Response<T> success(T data) {
        return success(data, currentRequestPath());
    }

    /** Creates a successful API response for an explicitly supplied request path. */
    public static <T> Response<T> success(T data, String path) {
        return new Response<>(true, "OK", "success", data, Instant.now().toString(), path);
    }

    /** Creates a failed API response with a stable machine-readable code. */
    public static Response<Void> error(String code, String message, String path) {
        return new Response<>(false, code, message, null, Instant.now().toString(), path);
    }

    private static String currentRequestPath() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRequestURI();
        }
        return "";
    }
}
