package io.okagent.service.dialogue;

import io.okagent.domain.dialogue.CustomerSentiment;
import io.okagent.domain.dialogue.DialogueOutcome;
import io.okagent.repository.dialogue.DialogueOutcomeRepository;
import io.okagent.repository.dialogue.DialogueSessionRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DialogueOutcomeServiceImpl implements DialogueOutcomeService {
    private final DialogueSessionRepository sessions;
    private final DialogueOutcomeRepository outcomes;

    public DialogueOutcomeServiceImpl(
            DialogueSessionRepository sessions, DialogueOutcomeRepository outcomes) {
        this.sessions = sessions;
        this.outcomes = outcomes;
    }

    @Override
    @Transactional(readOnly = true)
    public DialogueOutcomeView get(String sessionId) {
        requireSession(sessionId);
        return outcomes.findById(sessionId).map(this::toView).orElseGet(() -> emptyView(sessionId));
    }

    @Override
    @Transactional
    public DialogueOutcomeView save(
            String sessionId, DialogueOutcomeDraft draft, UUID actorAccountId) {
        requireSession(sessionId);
        Instant now = Instant.now();
        DialogueOutcome outcome = outcomes.findById(sessionId)
                .orElseGet(() -> new DialogueOutcome(sessionId, now));
        outcome.revise(
                draft.summary(),
                draft.customerNeed(),
                draft.intentLabel(),
                draft.productInterest(),
                draft.budget(),
                draft.purchaseTimeline(),
                draft.sentiment(),
                draft.resolutionCode(),
                draft.nextAction(),
                draft.followUpAt(),
                actorAccountId,
                now);
        return toView(outcomes.save(outcome));
    }

    private void requireSession(String sessionId) {
        if (!sessions.existsById(sessionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dialogue session not found");
        }
    }

    private DialogueOutcomeView emptyView(String sessionId) {
        return new DialogueOutcomeView(
                sessionId,
                null,
                null,
                null,
                null,
                null,
                null,
                CustomerSentiment.UNKNOWN,
                null,
                null,
                null,
                null,
                null,
                0);
    }

    private DialogueOutcomeView toView(DialogueOutcome outcome) {
        return new DialogueOutcomeView(
                outcome.getSessionId(),
                outcome.getSummary(),
                outcome.getCustomerNeed(),
                outcome.getIntentLabel(),
                outcome.getProductInterest(),
                outcome.getBudget(),
                outcome.getPurchaseTimeline(),
                outcome.getSentiment(),
                outcome.getResolutionCode(),
                outcome.getNextAction(),
                outcome.getFollowUpAt(),
                outcome.getUpdatedBy(),
                outcome.getUpdatedAt(),
                outcome.getRowVersion());
    }
}
