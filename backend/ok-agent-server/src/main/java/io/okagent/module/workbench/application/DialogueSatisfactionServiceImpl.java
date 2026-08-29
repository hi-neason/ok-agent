package io.okagent.module.workbench.application;

import io.okagent.module.conversation.domain.DialogueSatisfaction;
import io.okagent.module.conversation.infrastructure.persistence.DialogueSatisfactionRepository;
import io.okagent.module.conversation.infrastructure.persistence.DialogueSessionRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DialogueSatisfactionServiceImpl implements DialogueSatisfactionService {
    private final DialogueSessionRepository sessions;
    private final DialogueSatisfactionRepository satisfaction;

    public DialogueSatisfactionServiceImpl(
            DialogueSessionRepository sessions, DialogueSatisfactionRepository satisfaction) {
        this.sessions = sessions;
        this.satisfaction = satisfaction;
    }

    @Override
    @Transactional(readOnly = true)
    public DialogueSatisfactionView get(String sessionId) {
        requireSession(sessionId);
        return satisfaction.findById(sessionId).map(this::toView).orElseGet(() ->
                new DialogueSatisfactionView(sessionId, null, null, null, null, 0));
    }

    @Override
    @Transactional
    public DialogueSatisfactionView save(
            String sessionId, int rating, String feedback, UUID actorAccountId) {
        requireSession(sessionId);
        Instant now = Instant.now();
        DialogueSatisfaction value = satisfaction.findById(sessionId).orElse(null);
        if (value == null) {
            value = new DialogueSatisfaction(sessionId, rating, feedback, actorAccountId, now);
        } else {
            value.revise(rating, feedback, actorAccountId, now);
        }
        return toView(satisfaction.save(value));
    }

    private void requireSession(String sessionId) {
        if (!sessions.existsById(sessionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dialogue session not found");
        }
    }

    private DialogueSatisfactionView toView(DialogueSatisfaction value) {
        return new DialogueSatisfactionView(
                value.getSessionId(),
                value.getRating(),
                value.getFeedback(),
                value.getUpdatedBy(),
                value.getUpdatedAt(),
                value.getRowVersion());
    }
}
