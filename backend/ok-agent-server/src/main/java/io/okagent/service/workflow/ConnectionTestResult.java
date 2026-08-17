package io.okagent.service.workflow;

/**
 * Connection test outcome for a workflow source. {@code supported} is false when credentials work
 * but the remote app type cannot be triggered (e.g. a Dify chatflow when only workflows are supported).
 */
public record ConnectionTestResult(boolean success, boolean supported, String message, String remoteName) {
    public static ConnectionTestResult ok(String remoteName, String message) {
        return new ConnectionTestResult(true, true, message, remoteName);
    }

    public static ConnectionTestResult unsupported(String remoteName, String message) {
        return new ConnectionTestResult(true, false, message, remoteName);
    }

    public static ConnectionTestResult failed(String message) {
        return new ConnectionTestResult(false, false, message, null);
    }
}
