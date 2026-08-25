package io.okagent.service.customerwork;

import io.okagent.domain.customerwork.CustomerCaseType;
import java.util.List;
import java.util.UUID;

/** Converts conversation context into traceable sales leads and support tickets. */
public interface CustomerCaseService {
    List<CustomerCaseView> listForSession(String sessionId);

    CustomerCaseView createFromSession(String sessionId, CustomerCaseType type, UUID actorAccountId);
}
