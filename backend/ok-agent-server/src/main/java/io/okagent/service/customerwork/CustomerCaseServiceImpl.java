package io.okagent.service.customerwork;

import io.okagent.domain.customerwork.CustomerCase;
import io.okagent.domain.customerwork.CustomerCaseType;
import io.okagent.domain.dialogue.DialogueOutcome;
import io.okagent.domain.dialogue.DialogueSession;
import io.okagent.repository.customerwork.CustomerCaseRepository;
import io.okagent.repository.dialogue.DialogueOutcomeRepository;
import io.okagent.repository.dialogue.DialogueSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerCaseServiceImpl implements CustomerCaseService {
    private final CustomerCaseRepository cases;
    private final DialogueSessionRepository sessions;
    private final DialogueOutcomeRepository outcomes;

    public CustomerCaseServiceImpl(
            CustomerCaseRepository cases,
            DialogueSessionRepository sessions,
            DialogueOutcomeRepository outcomes) {
        this.cases = cases;
        this.sessions = sessions;
        this.outcomes = outcomes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerCaseView> listForSession(String sessionId) {
        requireSession(sessionId);
        return cases.findBySourceSessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional
    public CustomerCaseView createFromSession(
            String sessionId, CustomerCaseType type, UUID actorAccountId) {
        DialogueSession session = requireSession(sessionId);
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case type is required");
        }
        return cases.findBySourceSessionIdAndType(sessionId, type)
                .map(this::toView)
                .orElseGet(() -> create(session, type, actorAccountId));
    }

    private CustomerCaseView create(
            DialogueSession session, CustomerCaseType type, UUID actorAccountId) {
        DialogueOutcome outcome = outcomes.findById(session.getSessionId()).orElse(null);
        String title = firstPresent(
                outcome == null ? null : outcome.getProductInterest(),
                outcome == null ? null : outcome.getIntentLabel(),
                session.getTitle(),
                type == CustomerCaseType.LEAD ? "Conversation lead" : "Conversation ticket");
        String description = firstPresent(
                outcome == null ? null : outcome.getSummary(),
                outcome == null ? null : outcome.getCustomerNeed(),
                session.getTitle());
        CustomerCase customerCase = new CustomerCase(
                UUID.randomUUID(),
                type,
                title,
                session.getUserId(),
                session.getSessionId(),
                description,
                session.getPriority(),
                session.getAssigneeAccountId(),
                actorAccountId,
                Instant.now());
        return toView(cases.save(customerCase));
    }

    private DialogueSession requireSession(String sessionId) {
        return sessions.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dialogue session not found"));
    }

    private static String firstPresent(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) return candidate.trim();
        }
        return null;
    }

    private CustomerCaseView toView(CustomerCase value) {
        return new CustomerCaseView(
                value.getId(),
                value.getType(),
                value.getStatus(),
                value.getTitle(),
                value.getCustomerUserId(),
                value.getSourceSessionId(),
                value.getDescription(),
                value.getPriority(),
                value.getOwnerAccountId(),
                value.getCreatedAt());
    }
}
