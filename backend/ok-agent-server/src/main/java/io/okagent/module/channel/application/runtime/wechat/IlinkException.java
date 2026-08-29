package io.okagent.module.channel.application.runtime.wechat;

/** Thrown when an iLink HTTP call fails or returns an unexpected payload. */
public class IlinkException extends Exception {
    private final int statusCode;

    public IlinkException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public IlinkException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int statusCode() {
        return statusCode;
    }
}
