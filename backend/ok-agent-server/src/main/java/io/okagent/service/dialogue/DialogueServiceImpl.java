package io.okagent.service.dialogue;

import io.okagent.domain.agent.AgentAsset;
import io.okagent.domain.dialogue.DialogueSession;
import io.okagent.domain.dialogue.DialogueTurn;
import io.okagent.repository.agent.AgentAssetRepository;
import io.okagent.repository.dialogue.DialogueSessionRepository;
import io.okagent.repository.dialogue.DialogueTurnRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class DialogueServiceImpl implements DialogueService {

    private final DialogueSessionRepository sessions;
    private final DialogueTurnRepository turns;
    private final AgentAssetRepository agents;

    public DialogueServiceImpl(
            DialogueSessionRepository sessions, DialogueTurnRepository turns, AgentAssetRepository agents) {
        this.sessions = sessions;
        this.turns = turns;
        this.agents = agents;
    }

    @Override
    public boolean sessionExists(String sessionId) {
        return sessions.existsBySessionId(sessionId);
    }

    @Override
    public DialogueSession ensureSession(String sessionId, UUID agentId, String userId, String title) {
        Optional<DialogueSession> existing = sessions.findById(sessionId);
        if (existing.isPresent()) {
            return existing.get();
        }
        DialogueSession created =
                new DialogueSession(sessionId, agentId, title, userId, Instant.now());
        return sessions.save(created);
    }

    @Override
    public void recordMessage(String sessionId, String role, String content, String model, Integer latencyMs) {
        int seq = (int) turns.countBySessionId(sessionId) + 1;
        turns.save(new DialogueTurn(sessionId, seq, role, content, model, latencyMs, Instant.now()));
    }

    @Override
    public void touchSession(String sessionId) {
        sessions.findById(sessionId).ifPresent(s -> {
            s.setUpdatedAt(Instant.now());
            sessions.save(s);
        });
    }

    @Override
    public void purge(String sessionId) {
        turns.deleteBySessionId(sessionId);
        sessions.deleteById(sessionId);
    }

    @Override
    public java.util.List<DialogueTurn> getMessages(String sessionId) {
        return turns.findBySessionIdOrderBySeqAsc(sessionId);
    }

    @Override
    public java.util.List<DialogueSession> findByAgentId(UUID agentId) {
        return sessions.findByAgentIdOrderByUpdatedAtDesc(agentId);
    }

    @Override
    public Page<DialogueSummary> search(DialogueQuery query, int page, int size) {
        Specification<DialogueSession> spec = (root, cq, cb) -> {
            var predicate = cb.conjunction();
            if (query.sessionId() != null && !query.sessionId().isBlank()) {
                predicate = cb.and(predicate, cb.equal(root.get("sessionId"), query.sessionId()));
            }
            if (query.userId() != null && !query.userId().isBlank()) {
                predicate =
                        cb.and(predicate, cb.like(root.get("userId"), "%" + query.userId() + "%"));
            }
            if (query.agentId() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("agentId"), query.agentId()));
            }
            Parsed from = parse(query.from());
            if (from != null) {
                predicate =
                        cb.and(predicate, cb.greaterThanOrEqualTo(root.get("createdAt"), from.instant()));
            }
            Parsed to = parse(query.to());
            if (to != null) {
                Instant upper = to.dateOnly() ? to.instant().plus(1, ChronoUnit.DAYS) : to.instant();
                predicate = cb.and(predicate, cb.lessThan(root.get("createdAt"), upper));
            }
            return predicate;
        };
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return sessions.findAll(spec, pageable).map(this::toSummary);
    }

    private DialogueSummary toSummary(DialogueSession session) {
        String agentName = null;
        if (session.getAgentId() != null) {
            Optional<AgentAsset> asset = agents.findById(session.getAgentId());
            if (asset.isPresent()) {
                agentName = asset.get().getName();
            }
        }
        long turnCount = turns.countBySessionId(session.getSessionId());
        return new DialogueSummary(
                session.getSessionId(),
                session.getAgentId(),
                agentName,
                session.getTitle(),
                session.getUserId(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                turnCount);
    }

    private record Parsed(Instant instant, boolean dateOnly) {}

    /**
     * Accepts three boundary formats, in order of precision: an explicit instant
     * ({@code 2026-08-17T02:00:00Z}), a local date-time ({@code 2026-08-17T10:00:00}) and a plain
     * date ({@code 2026-08-17}). The two zone-less forms are interpreted in the server's zone,
     * which is what a human typing a bare date means; callers that need exactness (the console
     * sends browser-resolved instants) should pass the instant form.
     */
    private Parsed parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        value = value.trim();
        boolean dateOnly = value.length() == 10;
        try {
            if (dateOnly) {
                LocalDate date = LocalDate.parse(value);
                return new Parsed(
                        date.atStartOfDay(ZoneId.systemDefault()).toInstant(), true);
            }
            LocalDateTime dateTime = LocalDateTime.parse(value);
            return new Parsed(dateTime.atZone(ZoneId.systemDefault()).toInstant(), false);
        } catch (Exception ignored) {
            // fall through to instant parsing
        }
        try {
            return new Parsed(Instant.parse(value), false);
        } catch (Exception ignored) {
            return null;
        }
    }
}
