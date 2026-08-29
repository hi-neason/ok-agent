package io.okagent.module.workbench.api;

import io.okagent.module.workbench.application.DialogueOutcomeDraft;
import io.okagent.module.workbench.application.DialogueOutcomeService;
import io.okagent.module.workbench.application.DialogueOutcomeView;
import io.okagent.shared.api.Response;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/workbench/sessions/{sessionId}/outcome", "/api/v1/inbox/sessions/{sessionId}/outcome"})
public class DialogueOutcomeController {
    private final DialogueOutcomeService outcomes;

    public DialogueOutcomeController(DialogueOutcomeService outcomes) {
        this.outcomes = outcomes;
    }

    /** Returns the structured business result for a conversation, including an empty draft. */
    @GetMapping
    public Response<DialogueOutcomeView> get(@PathVariable String sessionId) {
        return Response.success(outcomes.get(sessionId));
    }

    /** Creates or replaces the structured business result and records the responsible operator. */
    @PutMapping
    public Response<DialogueOutcomeView> save(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DialogueOutcomeRequest request) {
        return Response.success(outcomes.save(
                sessionId,
                new DialogueOutcomeDraft(
                        request.summary(),
                        request.customerNeed(),
                        request.intentLabel(),
                        request.productInterest(),
                        request.budget(),
                        request.purchaseTimeline(),
                        request.sentiment(),
                        request.resolutionCode(),
                        request.nextAction(),
                        request.followUpAt()),
                UUID.fromString(jwt.getClaimAsString("accountId"))));
    }
}
