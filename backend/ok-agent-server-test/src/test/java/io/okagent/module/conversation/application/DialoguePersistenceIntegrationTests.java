package io.okagent.module.conversation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.okagent.module.agent.domain.AgentAsset;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.conversation.domain.DialogueTurn;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Deliberately has no test transaction: service entry points must open their own. */
@SpringBootTest
class DialoguePersistenceIntegrationTests {
    @Autowired DialogueService dialogue;
    @Autowired AgentAssetRepository agents;

    @Test
    void persistsBothChannelUserAndAssistantMessagesAcrossIndependentTransactions() {
        UUID agentId = UUID.randomUUID();
        String session = "test-" + UUID.randomUUID();
        agents.save(new AgentAsset(agentId, agentId.toString(), "Test", "", "GENERAL"));
        try {
            dialogue.ensureSession(session, agentId, "test-user", "Test");
            dialogue.recordChannelType(session, "FEISHU");
            dialogue.recordMessage(session, "user", "hello", null, null);
            dialogue.recordMessage(session, "assistant", "reply", null, 10, "test-trace");
            assertThat(dialogue.getMessages(session)).extracting(DialogueTurn::getRole)
                    .containsExactly("user", "assistant");
            assertThat(dialogue.getMessages(session)).extracting(DialogueTurn::getSeq)
                    .containsExactly(1, 2);
            assertThat(dialogue.nextSeq(session)).isEqualTo(3);
            assertThat(dialogue.findById(session).orElseThrow().getChannelType()).isEqualTo("FEISHU");
            // A failed insert must roll back sequence allocation, not leave a hole.
            assertThatThrownBy(() -> dialogue.recordMessage(session, "user", null, null, null))
                    .isInstanceOf(RuntimeException.class);
            assertThat(dialogue.nextSeq(session)).isEqualTo(3);
            dialogue.recordMessage(session, "user", "next", null, null);
            assertThat(dialogue.getMessages(session)).extracting(DialogueTurn::getSeq)
                    .containsExactly(1, 2, 3);
        } finally {
            dialogue.purge(session);
            agents.deleteById(agentId);
        }
    }
}
