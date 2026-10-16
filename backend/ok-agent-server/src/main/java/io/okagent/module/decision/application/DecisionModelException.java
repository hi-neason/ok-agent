package io.okagent.module.decision.application;

/** Sanitized failure classification; never carries provider bodies, prompts or credentials. */
public final class DecisionModelException extends RuntimeException {
    public enum Kind {
        AUTHENTICATION,
        RATE_LIMIT,
        PROVIDER,
        TIMEOUT,
        TRANSPORT,
        INVALID_RESPONSE,
        CONFIGURATION
    }

    private final Kind kind;
    private final int statusCode;

    public DecisionModelException(Kind kind, int statusCode) {
        super("Decision model call failed: " + kind);
        this.kind = kind;
        this.statusCode = statusCode;
    }

    public Kind kind() {
        return kind;
    }

    public int statusCode() {
        return statusCode;
    }
}
