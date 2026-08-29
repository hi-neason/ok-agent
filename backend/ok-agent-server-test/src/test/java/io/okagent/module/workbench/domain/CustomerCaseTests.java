package io.okagent.module.workbench.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.okagent.module.conversation.domain.DialoguePriority;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerCaseTests {

    @Test
    void initializesLeadAndTicketWithTheirBusinessOpeningStatus() {
        Instant now = Instant.parse("2026-08-25T02:00:00Z");
        UUID operator = UUID.randomUUID();

        CustomerCase lead = new CustomerCase(
                UUID.randomUUID(),
                CustomerCaseType.LEAD,
                "Enterprise plan",
                "customer-1",
                "session-1",
                "Customer requested pricing",
                DialoguePriority.HIGH,
                operator,
                operator,
                now);
        CustomerCase ticket = new CustomerCase(
                UUID.randomUUID(),
                CustomerCaseType.TICKET,
                "Login failure",
                "customer-1",
                "session-1",
                "Customer cannot sign in",
                DialoguePriority.URGENT,
                operator,
                operator,
                now);

        assertThat(lead.getStatus()).isEqualTo(CustomerCaseStatus.NEW);
        assertThat(ticket.getStatus()).isEqualTo(CustomerCaseStatus.OPEN);
        assertThat(lead.getSourceSessionId()).isEqualTo("session-1");
        assertThat(ticket.getCreatedBy()).isEqualTo(operator);
        assertThat(ticket.getCreatedAt()).isEqualTo(now);
    }
}
