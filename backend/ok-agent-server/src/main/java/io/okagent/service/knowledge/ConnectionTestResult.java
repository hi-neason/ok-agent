package io.okagent.service.knowledge;

/** Result of testing a knowledge source connection. */
public record ConnectionTestResult(boolean success, boolean supported, String message) {
    public static ConnectionTestResult ok(String message) {
        return new ConnectionTestResult(true, true, message);
    }

    public static ConnectionTestResult unsupported(String message) {
        return new ConnectionTestResult(false, false, message);
    }

    public static ConnectionTestResult failed(String message) {
        return new ConnectionTestResult(false, true, message);
    }
}
