package io.okagent.domain.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DialogueWorkItemLifecycleTests {

    @Test
    void supportsHandoffClaimResolutionClosureAndReopen() {
        Instant createdAt = Instant.parse("2026-08-25T01:00:00Z");
        Instant handoffAt = createdAt.plusSeconds(10);
        Instant assignedAt = createdAt.plusSeconds(20);
        Instant resolvedAt = createdAt.plusSeconds(30);
        Instant closedAt = createdAt.plusSeconds(40);
        Instant reopenedAt = createdAt.plusSeconds(50);
        UUID operator = UUID.randomUUID();
        DialogueSession session =
                new DialogueSession("session", UUID.randomUUID(), "Need help", "customer", createdAt);

        session.requestHumanHandoff(DialoguePriority.URGENT, operator, handoffAt);
        assertThat(session.getWorkStatus()).isEqualTo(DialogueWorkStatus.WAITING_HUMAN);
        assertThat(session.getPriority()).isEqualTo(DialoguePriority.URGENT);
        assertThat(session.getHandoffRequestedAt()).isEqualTo(handoffAt);

        session.assign(operator, operator, assignedAt);
        assertThat(session.getWorkStatus()).isEqualTo(DialogueWorkStatus.IN_PROGRESS);
        assertThat(session.getAssigneeAccountId()).isEqualTo(operator);
        assertThat(session.getAssignedAt()).isEqualTo(assignedAt);

        session.transitionTo(DialogueWorkStatus.RESOLVED, operator, resolvedAt);
        assertThat(session.getResolvedAt()).isEqualTo(resolvedAt);
        session.transitionTo(DialogueWorkStatus.CLOSED, operator, closedAt);
        assertThat(session.getClosedAt()).isEqualTo(closedAt);

        session.transitionTo(DialogueWorkStatus.IN_PROGRESS, operator, reopenedAt);
        assertThat(session.getResolvedAt()).isNull();
        assertThat(session.getClosedAt()).isNull();
        assertThat(session.getWorkItemUpdatedBy()).isEqualTo(operator);
        assertThat(session.getWorkItemUpdatedAt()).isEqualTo(reopenedAt);
    }

    @Test
    void rejectsHandoffAndAssignmentAfterClosure() {
        UUID operator = UUID.randomUUID();
        DialogueSession session = new DialogueSession(
                "session", UUID.randomUUID(), "Closed", "customer", Instant.parse("2026-08-25T01:00:00Z"));
        session.transitionTo(DialogueWorkStatus.CLOSED, operator, Instant.parse("2026-08-25T01:01:00Z"));

        assertThatThrownBy(() -> session.requestHumanHandoff(
                        DialoguePriority.HIGH, operator, Instant.parse("2026-08-25T01:02:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TERMINAL_CONVERSATION_CANNOT_REQUEST_HANDOFF");
        assertThatThrownBy(() -> session.assign(operator, operator, Instant.parse("2026-08-25T01:02:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CLOSED_CONVERSATION_CANNOT_BE_ASSIGNED");
    }

    @Test
    void rejectsInvalidLifecycleTransition() {
        DialogueSession session = new DialogueSession(
                "session", UUID.randomUUID(), "Waiting", "customer", Instant.parse("2026-08-25T01:00:00Z"));

        assertThatThrownBy(() -> session.transitionTo(
                        DialogueWorkStatus.WAITING_CUSTOMER,
                        UUID.randomUUID(),
                        Instant.parse("2026-08-25T01:01:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("INVALID_DIALOGUE_TRANSITION:OPEN->WAITING_CUSTOMER");
    }
}
