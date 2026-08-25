package io.okagent.module.customerchat.application;

/** Inbound customer-chat port implemented by the released-agent runtime. */
public interface CustomerChatService {
    /** Processes one customer message against the resolved released-agent configuration. */
    CustomerChatResult chat(CustomerChatCommand command);
}
