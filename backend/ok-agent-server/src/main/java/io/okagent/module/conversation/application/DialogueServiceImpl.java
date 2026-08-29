package io.okagent.module.conversation.application;

import io.okagent.module.agent.domain.AgentAsset;
import io.okagent.module.conversation.domain.DialogueSession;
import io.okagent.module.conversation.domain.DialogueTurn;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.conversation.infrastructure.persistence.DialogueSessionRepository;
import io.okagent.module.conversation.infrastructure.persistence.DialogueTurnRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    public void assertSessionOwner(String sessionId, UUID agentId, String userId) {
        sessions.findById(sessionId).ifPresent(existing -> requireOwner(existing, agentId, userId));
    }

    @Override
    public DialogueSession ensureSession(String sessionId, UUID agentId, String userId, String title) {
        Optional<DialogueSession> existing = sessions.findById(sessionId);
        if (existing.isPresent()) {
            return requireOwner(existing.get(), agentId, userId);
        }
        DialogueSession created = new DialogueSession(sessionId, agentId, title, userId, Instant.now());
        return sessions.save(created);
    }

    @Override
    public DialogueSession ensureSession(
            String sessionId, UUID agentId, UUID releaseId, Integer versionNo, String userId, String title) {
        Optional<DialogueSession> existing = sessions.findById(sessionId);
        if (existing.isPresent()) {
            return requireOwner(existing.get(), agentId, userId);
        }
        DialogueSession created = new DialogueSession(sessionId, agentId, title, userId, Instant.now());
        created.setReleaseInfo(releaseId, versionNo);
        return sessions.save(created);
    }

    @Override
    public DialogueTurn recordMessage(String sessionId, String role, String content, String model, Integer latencyMs) {
        return recordMessage(sessionId, role, content, model, latencyMs, null);
    }

    @Override
    public int nextSeq(String sessionId) {
        return sessions.findById(sessionId)
                .map(DialogueSession::getNextTurnSeq)
                .orElse(1);
    }

    @Override
    @Transactional
    public DialogueTurn recordMessage(
            String sessionId, String role, String content, String model, Integer latencyMs, String traceId) {
        DialogueSession session = sessions.findForTurnAllocation(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dialogue session not found"));
        int seq = session.allocateNextTurnSeq();
        DialogueTurn turn = new DialogueTurn(sessionId, seq, role, content, model, latencyMs, traceId, Instant.now());
        return turns.save(turn);
    }

    @Override
    public void touchSession(String sessionId) {
        sessions.findById(sessionId).ifPresent(s -> {
            s.setUpdatedAt(Instant.now());
            sessions.save(s);
        });
    }

    @Override
    @Transactional
    public void purge(String sessionId) {
        turns.deleteBySessionId(sessionId);
        sessions.deleteById(sessionId);
    }

    @Override
    public java.util.List<DialogueTurn> getMessages(String sessionId) {
        return turns.findBySessionIdOrderBySeqAsc(sessionId);
    }

    @Override
    public Optional<DialogueSession> findById(String sessionId) {
        return sessions.findById(sessionId);
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
                predicate = cb.and(predicate, cb.like(root.get("userId"), "%" + query.userId() + "%"));
            }
            if (query.agentId() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("agentId"), query.agentId()));
            }
            Parsed from = parse(query.from());
            if (from != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("createdAt"), from.instant()));
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

    private DialogueSession requireOwner(DialogueSession session, UUID agentId, String userId) {
        if (!Objects.equals(session.getAgentId(), agentId) || !Objects.equals(session.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session belongs to another agent or user");
        }
        return session;
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
                return new Parsed(date.atStartOfDay(ZoneId.systemDefault()).toInstant(), true);
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
