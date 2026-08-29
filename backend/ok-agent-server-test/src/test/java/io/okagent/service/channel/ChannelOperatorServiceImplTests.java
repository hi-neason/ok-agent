package io.okagent.service.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.okagent.domain.channel.ChannelOperatorAssignment;
import io.okagent.domain.channel.OperatorPresenceStatus;
import io.okagent.module.identity.domain.AccountRole;
import io.okagent.module.identity.domain.User;
import io.okagent.module.identity.domain.UserSource;
import io.okagent.repository.agent.AgentAssetRepository;
import io.okagent.repository.channel.ChannelAssetRepository;
import io.okagent.repository.channel.ChannelOperatorAssignmentRepository;
import io.okagent.repository.channel.OperatorPresenceRepository;
import io.okagent.module.identity.infrastructure.persistence.UserRepository;
import io.okagent.module.identity.application.AuthenticatedActor;
import io.okagent.module.identity.application.SecurityAuditService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ChannelOperatorServiceImplTests {
    private ChannelAssetRepository channels;
    private ChannelOperatorAssignmentRepository assignments;
    private OperatorPresenceRepository presences;
    private UserRepository users;
    private SecurityAuditService audit;
    private ChannelOperatorServiceImpl service;

    @BeforeEach
    void setUp() {
        channels = mock(ChannelAssetRepository.class);
        assignments = mock(ChannelOperatorAssignmentRepository.class);
        presences = mock(OperatorPresenceRepository.class);
        users = mock(UserRepository.class);
        audit = mock(SecurityAuditService.class);
        service = new ChannelOperatorServiceImpl(
                channels,
                assignments,
                presences,
                users,
                mock(AgentAssetRepository.class),
                mock(ChannelUserService.class),
                audit);
    }

    @Test
    void replacesAssignmentsWithEligibleOperatorsOnly() {
        UUID channelId = UUID.randomUUID();
        User operator = account(AccountRole.EDITOR, true);
        AuthenticatedActor actor = new AuthenticatedActor(UUID.randomUUID(), "admin");
        when(channels.existsById(channelId)).thenReturn(true);
        when(users.findBySourceAndPasswordHashIsNotNullAndEnabledTrueOrderByDisplayNameAsc(UserSource.CONSOLE))
                .thenReturn(List.of(operator));
        when(assignments.save(any(ChannelOperatorAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.replaceAssignments(channelId, Set.of(operator.getId()), actor);

        assertThat(result).singleElement().satisfies(view -> assertThat(view.assigned()).isTrue());
        verify(assignments).deleteByChannelId(channelId);
        verify(assignments).flush();
        verify(audit).record(actor, "CHANNEL_OPERATORS_REPLACED", "CHANNEL", channelId.toString(), "operatorCount=1");
    }

    @Test
    void rejectsViewerAssignment() {
        UUID channelId = UUID.randomUUID();
        User viewer = account(AccountRole.VIEWER, true);
        when(channels.existsById(channelId)).thenReturn(true);
        when(users.findBySourceAndPasswordHashIsNotNullAndEnabledTrueOrderByDisplayNameAsc(UserSource.CONSOLE))
                .thenReturn(List.of(viewer));

        assertThatThrownBy(() -> service.replaceAssignments(
                        channelId,
                        Set.of(viewer.getId()),
                        new AuthenticatedActor(UUID.randomUUID(), "admin")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void defaultsEligibleOperatorPresenceToOffline() {
        User operator = account(AccountRole.EDITOR, true);
        when(users.findById(operator.getId())).thenReturn(java.util.Optional.of(operator));
        when(presences.findById(operator.getId())).thenReturn(java.util.Optional.empty());

        assertThat(service.getPresence(operator.getId()).status()).isEqualTo(OperatorPresenceStatus.OFFLINE);
    }

    private static User account(AccountRole role, boolean enabled) {
        User user = new User(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                role.name().toLowerCase(),
                role.name(),
                null,
                null,
                null,
                enabled);
        user.initializeCredentials("hash", role);
        return user;
    }
}
