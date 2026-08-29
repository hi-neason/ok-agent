package io.okagent.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** Stable envelope returned by every JSON API endpoint. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        String timestamp,
        String path) {

    /** Creates a successful API response. */
    public static <T> ApiResponse<T> success(T data, String path) {
        return new ApiResponse<>(true, "OK", "success", data, Instant.now().toString(), path);
    }

    /** Creates a failed API response with a stable machine-readable code. */
    public static ApiResponse<Void> error(String code, String message, String path) {
        return new ApiResponse<>(false, code, message, null, Instant.now().toString(), path);
    }
}
