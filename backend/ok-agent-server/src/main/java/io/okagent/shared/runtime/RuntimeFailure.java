package io.okagent.shared.runtime;

import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;

/** Stable, sanitized runtime failure with a correlation identifier. */
public class RuntimeFailure extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final String traceId;
    private RuntimeFailure(String code, HttpStatus status, String traceId) {
        super(code);
        this.code = code; this.status = status; this.traceId = traceId;
    }
    public static RuntimeFailure from(Throwable failure, String traceId) {
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (var cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof java.net.http.HttpTimeoutException) {
                return new RuntimeFailure("RUNTIME_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT, traceId);
            }
            if (cause instanceof io.agentscope.extensions.model.openai.exception.OpenAIException provider
                    && Integer.valueOf(429).equals(provider.getStatusCode())) {
                return new RuntimeFailure("MODEL_RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS, traceId);
            }
        }
        return new RuntimeFailure("RUNTIME_UPSTREAM_FAILURE", HttpStatus.BAD_GATEWAY, traceId);
    }
    public String code() { return code; }
    public HttpStatus status() { return status; }
    public String traceId() { return traceId; }
}
