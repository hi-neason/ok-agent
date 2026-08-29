package io.okagent.service.channel;

import io.okagent.domain.channel.ChannelOperatorAssignment;
import io.okagent.domain.channel.OperatorPresence;
import io.okagent.domain.channel.OperatorPresenceStatus;
import io.okagent.module.identity.domain.AccountRole;
import io.okagent.module.identity.domain.User;
import io.okagent.module.identity.domain.UserSource;
import io.okagent.module.workbench.api.MyChannelResponse;
import io.okagent.module.workbench.api.OperatorPresenceResponse;
import io.okagent.repository.agent.AgentAssetRepository;
import io.okagent.repository.channel.ChannelAssetRepository;
import io.okagent.repository.channel.ChannelOperatorAssignmentRepository;
import io.okagent.repository.channel.OperatorPresenceRepository;
import io.okagent.module.identity.infrastructure.persistence.UserRepository;
import io.okagent.module.identity.application.AuthenticatedActor;
import io.okagent.module.identity.application.SecurityAuditService;
import io.okagent.web.channel.ChannelOperatorResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChannelOperatorServiceImpl implements ChannelOperatorService {
    private final ChannelAssetRepository channels;
    private final ChannelOperatorAssignmentRepository assignments;
    private final OperatorPresenceRepository presences;
    private final UserRepository users;
    private final AgentAssetRepository agents;
    private final ChannelUserService channelUsers;
    private final SecurityAuditService audit;

    public ChannelOperatorServiceImpl(
            ChannelAssetRepository channels,
            ChannelOperatorAssignmentRepository assignments,
            OperatorPresenceRepository presences,
            UserRepository users,
            AgentAssetRepository agents,
            ChannelUserService channelUsers,
            SecurityAuditService audit) {
        this.channels = channels;
        this.assignments = assignments;
        this.presences = presences;
        this.users = users;
        this.agents = agents;
        this.channelUsers = channelUsers;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChannelOperatorResponse> listOperators(UUID channelId) {
        requireChannel(channelId);
        Set<UUID> assigned = assignments.findByChannelIdOrderByCreatedAtAsc(channelId).stream()
                .map(ChannelOperatorAssignment::getOperatorAccountId)
                .collect(java.util.stream.Collectors.toSet());
        return eligibleOperators().stream().map(user -> toOperator(user, assigned.contains(user.getId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> assignedOperatorNames(UUID channelId) {
        List<UUID> accountIds = assignments.findByChannelIdOrderByCreatedAtAsc(channelId).stream()
                .map(ChannelOperatorAssignment::getOperatorAccountId)
                .toList();
        if (accountIds.isEmpty()) {
            return List.of();
        }
        java.util.Map<UUID, User> usersById = users.findAllById(accountIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, user -> user));
        return accountIds.stream()
                .map(usersById::get)
                .filter(java.util.Objects::nonNull)
                .map(user -> user.getDisplayName() == null || user.getDisplayName().isBlank()
                        ? user.getUsername()
                        : user.getDisplayName())
                .toList();
    }

    @Override
    @Transactional
    public List<ChannelOperatorResponse> replaceAssignments(
            UUID channelId, Set<UUID> operatorAccountIds, AuthenticatedActor actor) {
        requireChannel(channelId);
        Set<UUID> requested = new HashSet<>(operatorAccountIds == null ? Set.of() : operatorAccountIds);
        List<User> eligible = eligibleOperators();
        Set<UUID> eligibleIds = eligible.stream().map(User::getId).collect(java.util.stream.Collectors.toSet());
        if (!eligibleIds.containsAll(requested)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CHANNEL_OPERATOR_NOT_ELIGIBLE");
        }
        assignments.deleteByChannelId(channelId);
        // Flush the deferred delete before inserting the replacement set. Otherwise an operator
        // retained in the selection can hit the channel/operator unique constraint.
        assignments.flush();
        requested.forEach(accountId -> assignments.save(
                new ChannelOperatorAssignment(UUID.randomUUID(), channelId, accountId, actor.accountId())));
        audit.record(
                actor,
                "CHANNEL_OPERATORS_REPLACED",
                "CHANNEL",
                channelId.toString(),
                "operatorCount=" + requested.size());
        return eligible.stream().map(user -> toOperator(user, requested.contains(user.getId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyChannelResponse> listMyChannels(UUID operatorAccountId) {
        requireEligibleOperator(operatorAccountId);
        return assignments.findByOperatorAccountIdOrderByCreatedAtAsc(operatorAccountId).stream()
                .map(assignment -> channels.findById(assignment.getChannelId())
                        .map(channel -> new MyChannelResponse(
                                channel.getId(),
                                channel.getName(),
                                channel.getType(),
                                channel.getRuntimeStatus(),
                                channel.isEnabled(),
                                channel.getBoundAgentId(),
                                channel.getBoundAgentId() == null
                                        ? null
                                        : agents.findById(channel.getBoundAgentId())
                                                .map(agent -> agent.getName())
                                                .orElse(null),
                                channelUsers.countByChannel(channel.getChannelKey()),
                                assignments.findByChannelIdOrderByCreatedAtAsc(channel.getId()).size(),
                                assignment.getCreatedAt()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OperatorPresenceResponse getPresence(UUID operatorAccountId) {
        requireEligibleOperator(operatorAccountId);
        return presences.findById(operatorAccountId)
                .map(value -> new OperatorPresenceResponse(value.getStatus(), value.getUpdatedAt()))
                .orElseGet(() -> new OperatorPresenceResponse(OperatorPresenceStatus.OFFLINE, null));
    }

    @Override
    @Transactional
    public OperatorPresenceResponse setPresence(UUID operatorAccountId, OperatorPresenceStatus status) {
        requireEligibleOperator(operatorAccountId);
        OperatorPresence presence = presences.findById(operatorAccountId)
                .orElseGet(() -> new OperatorPresence(operatorAccountId));
        presence.changeTo(status);
        OperatorPresence saved = presences.save(presence);
        return new OperatorPresenceResponse(saved.getStatus(), saved.getUpdatedAt());
    }

    private List<User> eligibleOperators() {
        return users.findBySourceAndPasswordHashIsNotNullAndEnabledTrueOrderByDisplayNameAsc(UserSource.CONSOLE)
                .stream()
                .filter(user -> user.getRole() != AccountRole.VIEWER)
                .toList();
    }

    private User requireEligibleOperator(UUID accountId) {
        return users.findById(accountId)
                .filter(User::isEnabled)
                .filter(User::hasCredentials)
                .filter(user -> user.getSource() == UserSource.CONSOLE && user.getRole() != AccountRole.VIEWER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "OPERATOR_ACCOUNT_REQUIRED"));
    }

    private void requireChannel(UUID channelId) {
        if (!channels.existsById(channelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CHANNEL_NOT_FOUND");
        }
    }

    private static ChannelOperatorResponse toOperator(User user, boolean assigned) {
        return new ChannelOperatorResponse(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), assigned);
    }
}
