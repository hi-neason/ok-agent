package io.okagent.shared.runtime;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.concurrent.TimeoutException;

/** Produces short tool-result failures safe to pass back into an agent context. */
public final class ToolFailureMessage {
    private ToolFailureMessage() {}

    public static String of(String toolName, Throwable failure) {
        return "Tool failed: " + toolName + " (" + code(failure) + ").";
    }

    private static String code(Throwable failure) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException) {
                return "INVALID_ARGUMENT";
            }
            if (cause instanceof SecurityException) {
                return "ACCESS_DENIED";
            }
            if (cause instanceof TimeoutException || cause instanceof java.net.http.HttpTimeoutException) {
                return "REMOTE_TIMEOUT";
            }
        }
        return "REMOTE_UPSTREAM_FAILURE";
    }
}
