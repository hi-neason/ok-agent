package io.okagent.module.workbench.api;

import io.okagent.module.workbench.application.*;

import io.okagent.module.workbench.application.CustomerCaseService;
import io.okagent.module.workbench.application.CustomerCaseView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({
    "/api/v1/workbench/sessions/{sessionId}/cases",
    "/api/v1/inbox/sessions/{sessionId}/cases"
})
public class CustomerCaseController {
    private final CustomerCaseService cases;

    public CustomerCaseController(CustomerCaseService cases) {
        this.cases = cases;
    }

    /** Lists leads and support tickets created from the source conversation. */
    @GetMapping
    public List<CustomerCaseView> list(@PathVariable String sessionId) {
        return cases.listForSession(sessionId);
    }

    /** Idempotently converts a conversation into a lead or support ticket. */
    @PostMapping
    public CustomerCaseView create(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCustomerCaseRequest request) {
        return cases.createFromSession(
                sessionId,
                request.type(),
                UUID.fromString(jwt.getClaimAsString("accountId")));
    }
}
