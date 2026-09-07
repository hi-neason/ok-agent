package io.okagent.module.persona.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.conversation.application.DialogueService;
import io.okagent.module.model.application.ApiKeyCipher;
import io.okagent.module.model.infrastructure.persistence.ModelAssetRepository;
import io.okagent.module.persona.infrastructure.persistence.UserPersonaRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonaExtractionServiceTests {

    @Test
    void ignoresExtractionRequestsAfterShutdown() {
        var agents = mock(AgentAssetRepository.class);
        var service = new PersonaExtractionService(
                agents,
                mock(ModelAssetRepository.class),
                mock(ApiKeyCipher.class),
                mock(DialogueService.class),
                mock(UserPersonaRepository.class),
                mock(UserPersonaService.class),
                new ObjectMapper());
        service.shutdown();

        assertThatCode(() -> service.extractAsync(UUID.randomUUID(), "user", "session"))
                .doesNotThrowAnyException();
        verifyNoInteractions(agents);
    }
}
