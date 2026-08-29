package io.okagent.module.workbench.api;

import io.okagent.module.workbench.application.*;

import io.okagent.module.workbench.application.DialogueSatisfactionService;
import io.okagent.module.workbench.application.DialogueSatisfactionView;
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
@RequestMapping({
    "/api/v1/workbench/sessions/{sessionId}/satisfaction",
    "/api/v1/inbox/sessions/{sessionId}/satisfaction"
})
public class DialogueSatisfactionController {
    private final DialogueSatisfactionService satisfaction;

    public DialogueSatisfactionController(DialogueSatisfactionService satisfaction) {
        this.satisfaction = satisfaction;
    }

    /** Returns customer satisfaction for a conversation, including an unrated draft. */
    @GetMapping
    public DialogueSatisfactionView get(@PathVariable String sessionId) {
        return satisfaction.get(sessionId);
    }

    /** Records a validated five-point satisfaction score and optional feedback. */
    @PutMapping
    public DialogueSatisfactionView save(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DialogueSatisfactionRequest request) {
        return satisfaction.save(
                sessionId,
                request.rating(),
                request.feedback(),
                UUID.fromString(jwt.getClaimAsString("accountId")));
    }
}
