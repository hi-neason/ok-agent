package io.okagent.web.chat;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionChatRequestValidationTests {

    @Test
    void rejectsMissingIdentityAndOversizedSessionFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = new ProductionChatRequest(
                    UUID.randomUUID(), "c".repeat(129), "s".repeat(129), " ", " ");

            assertThat(validator.validate(request))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("channelId", "sessionId", "userId", "message");
        }
    }
}
