package io.okagent.module.conversation.application;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import io.okagent.module.conversation.domain.*;
import io.okagent.module.conversation.infrastructure.persistence.DialogueSessionRepository;
class DialogueAutomationTests {
    @Test void handoffSuppressesRepliesUntilExplicitResume() {
        var repo = mock(DialogueSessionRepository.class);
        var service = new DialogueServiceImpl(repo, null, null);
        var actor = UUID.randomUUID();
        var session = new DialogueSession("s", UUID.randomUUID(), "title", "u", Instant.now());
        when(repo.findById("s")).thenReturn(Optional.of(session));
        assertThat(service.allowsAutomation("s")).isTrue();
        session.requestHumanHandoff(null, actor, Instant.now());
        assertThat(service.allowsAutomation("s")).isFalse();
        session.assign(actor, actor, Instant.now());
        assertThat(service.allowsAutomation("s")).isFalse();
        session.resumeAutomation(actor, Instant.now());
        assertThat(service.allowsAutomation("s")).isTrue();
        assertThat(session.getAssigneeAccountId()).isNull();
        assertThat(service.allowsAutomation("missing")).isFalse();
    }
}
