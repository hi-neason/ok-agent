package io.okagent.module.conversation.domain;

/** Queue priority used to order conversations requiring operational attention. */
public enum DialoguePriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}
