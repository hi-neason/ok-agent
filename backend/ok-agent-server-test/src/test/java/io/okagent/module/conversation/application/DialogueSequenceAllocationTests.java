package io.okagent.module.conversation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.okagent.module.conversation.domain.DialogueSession;
import io.okagent.module.conversation.domain.DialogueTurn;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.conversation.infrastructure.persistence.DialogueSessionRepository;
import io.okagent.module.conversation.infrastructure.persistence.DialogueTurnRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DialogueSequenceAllocationTests {

    @Test
    void allocatesDistinctSequencesFromTheLockedSessionCounter() {
        DialogueSession session =
                new DialogueSession("session", UUID.randomUUID(), "title", "user", Instant.now());
        DialogueSessionRepository sessions = mock(DialogueSessionRepository.class);
        DialogueTurnRepository turns = mock(DialogueTurnRepository.class);
        when(sessions.findForTurnAllocation("session")).thenReturn(Optional.of(session));
        var saved = new ArrayList<DialogueTurn>();
        when(turns.save(org.mockito.ArgumentMatchers.any(DialogueTurn.class)))
                .thenAnswer(invocation -> {
                    DialogueTurn turn = invocation.getArgument(0);
                    saved.add(turn);
                    return turn;
                });
        DialogueServiceImpl service =
                new DialogueServiceImpl(sessions, turns, mock(AgentAssetRepository.class));

        service.recordMessage("session", "user", "first", null, null, null);
        service.recordMessage("session", "assistant", "second", null, null, null);

        assertThat(saved).extracting(DialogueTurn::getSeq).containsExactly(1, 2);
        assertThat(session.getNextTurnSeq()).isEqualTo(3);
    }
}
