package io.okagent.service.dialogue;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.okagent.domain.dialogue.DialogueSession;
import io.okagent.repository.agent.AgentAssetRepository;
import io.okagent.repository.dialogue.DialogueSessionRepository;
import io.okagent.repository.dialogue.DialogueTurnRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DialogueSessionOwnershipTests {

    @Test
    void rejectsReusingAnExistingSessionForAnotherUserOrAgent() {
        UUID ownerAgent = UUID.randomUUID();
        DialogueSession existing = new DialogueSession("session", ownerAgent, "title", "owner", Instant.now());
        DialogueSessionRepository sessions = mock(DialogueSessionRepository.class);
        when(sessions.findById("session")).thenReturn(Optional.of(existing));
        DialogueServiceImpl service = new DialogueServiceImpl(
                sessions, mock(DialogueTurnRepository.class), mock(AgentAssetRepository.class));

        assertForbidden(() -> service.assertSessionOwner("session", ownerAgent, "attacker"));
        assertForbidden(() -> service.ensureSession("session", UUID.randomUUID(), "owner", "title"));
    }

    private void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN));
    }
}
