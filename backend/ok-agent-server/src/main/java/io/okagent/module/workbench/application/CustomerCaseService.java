package io.okagent.module.workbench.application;

import io.okagent.module.workbench.domain.CustomerCaseType;
import java.util.List;
import java.util.UUID;

/** Converts conversation context into traceable sales leads and support tickets. */
public interface CustomerCaseService {
    /** Lists the sales leads and support tickets created from one conversation. */
    List<CustomerCaseView> listForSession(String sessionId);

    /** Creates a traceable sales lead or support ticket from the current conversation context. */
    CustomerCaseView createFromSession(String sessionId, CustomerCaseType type, UUID actorAccountId);
}
