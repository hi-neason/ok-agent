package io.okagent.module.channel.domain;

/** WeChat iLink (ClawBot) QR-login lifecycle state for a channel session. */
public enum IlinkLoginStatus {
    /** No bot_token yet; the channel cannot long-poll. */
    LOGGED_OUT,
    /** A QR code has been requested and is awaiting a scan. */
    WAITING_QR,
    /** The QR was scanned but not yet confirmed on the phone. */
    SCANNED,
    /** Scan confirmed; a bot_token is present and polling can run. */
    LOGGED_IN,
    /** The QR expired; a new one must be requested. */
    EXPIRED,
    /** Login or polling failed; see lastError. */
    ERROR
}
