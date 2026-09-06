package io.okagent.module.conversation.application;
import static org.assertj.core.api.Assertions.*;
import io.okagent.module.conversation.domain.*;
import io.okagent.module.conversation.infrastructure.persistence.DialogueSessionRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
@SpringBootTest
@Transactional
class CustomerPaginationTests {
    @Autowired DialogueSessionRepository sessions;
    @Test void pagesCustomersWithoutSplittingTheirSessionsOrMergingAnonymousUsers() {
        sessions.deleteAll();
        UUID agent = UUID.randomUUID();
        for (String id : new String[]{"a", "b", "c", "d"}) {
            sessions.save(new DialogueSession(id, agent, id, id.equals("a") || id.equals("b") ? "same" : null, Instant.now()));
        }
        sessions.flush();
        var page = sessions.customerKeys(null, PageRequest.of(0, 2));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
        var all = sessions.customerKeys(null, PageRequest.of(0, 10));
        assertThat(all.getContent()).contains("user:same", "anonymous:c", "anonymous:d");
        assertThat(sessions.customerSessions(java.util.List.of("user:same"), null)).hasSize(2);
        assertThat(sessions.customerKeys(DialogueWorkStatus.WAITING_HUMAN, PageRequest.of(0, 10)).getContent()).isEmpty();
    }
}
