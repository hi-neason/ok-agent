package io.okagent.domain.dialogue;

/** Operational lifecycle of a customer conversation in the shared service inbox. */
public enum DialogueWorkStatus {
    OPEN,
    WAITING_HUMAN,
    IN_PROGRESS,
    WAITING_CUSTOMER,
    RESOLVED,
    CLOSED
}
