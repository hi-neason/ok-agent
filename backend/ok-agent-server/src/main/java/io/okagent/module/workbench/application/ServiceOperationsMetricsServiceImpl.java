package io.okagent.module.workbench.application;

import io.okagent.module.workbench.domain.CustomerCaseType;
import io.okagent.module.conversation.domain.DialogueWorkStatus;
import io.okagent.module.workbench.infrastructure.persistence.CustomerCaseRepository;
import io.okagent.module.conversation.infrastructure.persistence.DialogueSatisfactionRepository;
import io.okagent.module.conversation.infrastructure.persistence.DialogueSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceOperationsMetricsServiceImpl implements ServiceOperationsMetricsService {
    private final DialogueSessionRepository sessions;
    private final CustomerCaseRepository cases;
    private final DialogueSatisfactionRepository satisfaction;

    public ServiceOperationsMetricsServiceImpl(
            DialogueSessionRepository sessions,
            CustomerCaseRepository cases,
            DialogueSatisfactionRepository satisfaction) {
        this.sessions = sessions;
        this.cases = cases;
        this.satisfaction = satisfaction;
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceOperationsMetricsView get() {
        return new ServiceOperationsMetricsView(
                sessions.count(),
                sessions.countByWorkStatus(DialogueWorkStatus.WAITING_HUMAN),
                sessions.countByWorkStatus(DialogueWorkStatus.IN_PROGRESS),
                sessions.countByWorkStatus(DialogueWorkStatus.RESOLVED),
                cases.countByType(CustomerCaseType.LEAD),
                cases.countByType(CustomerCaseType.TICKET),
                satisfaction.count(),
                satisfaction.averageRating());
    }
}
