package io.okagent.module.workbench.application;

import io.okagent.module.conversation.domain.DialoguePriority;
import io.okagent.module.conversation.domain.DialogueSession;
import io.okagent.module.conversation.domain.DialogueWorkStatus;
import io.okagent.module.identity.domain.User;
import io.okagent.module.identity.domain.UserSource;
import io.okagent.module.identity.domain.AccountRole;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.conversation.infrastructure.persistence.DialogueSessionRepository;
import io.okagent.module.conversation.infrastructure.persistence.DialogueTurnRepository;
import io.okagent.module.identity.infrastructure.persistence.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DialogueWorkItemServiceImpl implements DialogueWorkItemService {
    private final DialogueSessionRepository sessions;
    private final DialogueTurnRepository turns;
    private final AgentAssetRepository agents;
    private final UserRepository users;

    public DialogueWorkItemServiceImpl(
            DialogueSessionRepository sessions,
            DialogueTurnRepository turns,
            AgentAssetRepository agents,
            UserRepository users) {
        this.sessions = sessions;
        this.turns = turns;
        this.agents = agents;
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DialogueOperatorView> listOperators() {
        return users.findBySourceAndPasswordHashIsNotNullAndEnabledTrueOrderByDisplayNameAsc(UserSource.CONSOLE)
                .stream()
                .filter(user -> user.getRole() != AccountRole.VIEWER)
                .map(user -> new DialogueOperatorView(
                        user.getId(), user.getUsername(), user.getDisplayName(), user.getRole()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DialogueWorkItemView> list(DialogueWorkItemQuery query, int page, int size) {
        Specification<DialogueSession> spec = (root, cq, cb) -> {
            var predicate = cb.conjunction();
            if (query.status() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("workStatus"), query.status()));
            }
            if (query.priority() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("priority"), query.priority()));
            }
            if (query.assigneeAccountId() != null) {
                predicate = cb.and(
                        predicate, cb.equal(root.get("assigneeAccountId"), query.assigneeAccountId()));
            }
            if (query.unassigned()) {
                predicate = cb.and(predicate, cb.isNull(root.get("assigneeAccountId")));
            }
            if (query.userId() != null && !query.userId().isBlank()) {
                predicate = cb.and(predicate, cb.like(root.get("userId"), "%" + query.userId().trim() + "%"));
            }
            if (query.agentId() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("agentId"), query.agentId()));
            }
            return predicate;
        };
        var sort = Sort.by(
                Sort.Order.desc("priorityRank"),
                Sort.Order.desc("handoffRequestedAt").nullsLast(),
                Sort.Order.desc("updatedAt"));
        return sessions.findAll(spec, PageRequest.of(page, size, sort)).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public DialogueWorkItemView get(String sessionId) {
        return toView(require(sessionId));
    }

    @Override
    @Transactional
    public DialogueWorkItemView requestHandoff(
            String sessionId, DialoguePriority priority, UUID actorAccountId) {
        DialogueSession session = require(sessionId);
        apply(() -> session.requestHumanHandoff(priority, actorAccountId, Instant.now()));
        return toView(sessions.save(session));
    }

    @Override
    @Transactional
    public DialogueWorkItemView claim(String sessionId, UUID actorAccountId) {
        requireAssignableAccount(actorAccountId);
        DialogueSession session = require(sessionId);
        apply(() -> session.assign(actorAccountId, actorAccountId, Instant.now()));
        return toView(sessions.save(session));
    }

    @Override
    @Transactional
    public DialogueWorkItemView assign(
            String sessionId, UUID assigneeAccountId, UUID actorAccountId) {
        if (assigneeAccountId != null) {
            requireAssignableAccount(assigneeAccountId);
        }
        DialogueSession session = require(sessionId);
        apply(() -> session.assign(assigneeAccountId, actorAccountId, Instant.now()));
        return toView(sessions.save(session));
    }

    @Override
    @Transactional
    public DialogueWorkItemView transition(
            String sessionId, DialogueWorkStatus status, UUID actorAccountId) {
        DialogueSession session = require(sessionId);
        apply(() -> session.transitionTo(status, actorAccountId, Instant.now()));
        return toView(sessions.save(session));
    }

    @Override
    @Transactional
    public DialogueWorkItemView changePriority(
            String sessionId, DialoguePriority priority, UUID actorAccountId) {
        DialogueSession session = require(sessionId);
        apply(() -> session.changePriority(priority, actorAccountId, Instant.now()));
        return toView(sessions.save(session));
    }

    private DialogueSession require(String sessionId) {
        return sessions.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dialogue session not found"));
    }

    private User requireAssignableAccount(UUID accountId) {
        User account = users.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee account not found"));
        if (account.getSource() != UserSource.CONSOLE
                || !account.hasCredentials()
                || !account.isEnabled()
                || account.getRole() == AccountRole.VIEWER) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Assignee must be an enabled admin or editor account");
        }
        return account;
    }

    private void apply(Runnable mutation) {
        try {
            mutation.run();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    private DialogueWorkItemView toView(DialogueSession session) {
        String agentName = session.getAgentId() == null
                ? null
                : agents.findById(session.getAgentId()).map(agent -> agent.getName()).orElse(null);
        String customerName = findByUserId(session.getUserId()).map(User::getDisplayName).orElse(null);
        String assigneeName = session.getAssigneeAccountId() == null
                ? null
                : users.findById(session.getAssigneeAccountId()).map(User::getDisplayName).orElse(null);
        return new DialogueWorkItemView(
                session.getSessionId(),
                session.getAgentId(),
                agentName,
                session.getTitle(),
                session.getUserId(),
                customerName,
                session.getWorkStatus(),
                session.getPriority(),
                session.getAssigneeAccountId(),
                assigneeName,
                session.getHandoffRequestedAt(),
                session.getAssignedAt(),
                session.getResolvedAt(),
                session.getClosedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                turns.countBySessionId(session.getSessionId()),
                session.getRowVersion());
    }

    private Optional<User> findByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return users.findByUserId(userId);
    }
}
