package io.okagent.module.workbench.api;

import io.okagent.module.conversation.domain.DialoguePriority;
import io.okagent.module.conversation.domain.DialogueWorkStatus;
import io.okagent.module.workbench.application.*;
import io.okagent.module.workbench.application.DialogueOperatorView;
import io.okagent.module.workbench.application.DialogueWorkItemQuery;
import io.okagent.module.workbench.application.DialogueWorkItemService;
import io.okagent.module.workbench.application.DialogueWorkItemView;
import io.okagent.shared.api.ApiResponse;
import io.okagent.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/workbench/sessions", "/api/v1/inbox/sessions"})
@Validated
public class DialogueInboxController {
    private final DialogueWorkItemService workItems;

    public DialogueInboxController(DialogueWorkItemService workItems) {
        this.workItems = workItems;
    }

    /** Lists enabled console operators available for inbox assignment. */
    @GetMapping("/operators")
    public ApiResponse<List<DialogueOperatorView>> operators() {
        return ApiResponse.success(workItems.listOperators());
    }

    /** Lists actionable conversations using queue, owner, customer, and agent filters. */
    @GetMapping
    public ApiResponse<PageResponse<DialogueWorkItemView>> list(
            @RequestParam(required = false) DialogueWorkStatus status,
            @RequestParam(required = false) DialoguePriority priority,
            @RequestParam(required = false) UUID assigneeAccountId,
            @RequestParam(defaultValue = "false") boolean unassigned,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) UUID agentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.of(workItems.list(
                new DialogueWorkItemQuery(status, priority, assigneeAccountId, unassigned, userId, agentId),
                page,
                size)));
    }

    /** Returns one actionable conversation for the inbox detail panel. */
    @GetMapping("/{sessionId}")
    public ApiResponse<DialogueWorkItemView> get(@PathVariable String sessionId) {
        return ApiResponse.success(workItems.get(sessionId));
    }

    /** Places a conversation in the human queue and records the handoff timestamp. */
    @PostMapping("/{sessionId}/handoff")
    public ApiResponse<DialogueWorkItemView> requestHandoff(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) HandoffRequest request) {
        DialoguePriority priority = request == null ? null : request.priority();
        return ApiResponse.success(workItems.requestHandoff(sessionId, priority, actorId(jwt)));
    }

    /** Claims a conversation for the currently authenticated operator. */
    @PostMapping("/{sessionId}/claim")
    public ApiResponse<DialogueWorkItemView> claim(@PathVariable String sessionId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(workItems.claim(sessionId, actorId(jwt)));
    }

    /** Assigns a conversation to an enabled console account or returns it to the queue. */
    @PutMapping("/{sessionId}/assignment")
    public ApiResponse<DialogueWorkItemView> assign(
            @PathVariable String sessionId, @AuthenticationPrincipal Jwt jwt, @RequestBody AssignmentRequest request) {
        return ApiResponse.success(workItems.assign(sessionId, request.assigneeAccountId(), actorId(jwt)));
    }

    /** Applies a validated conversation lifecycle transition. */
    @PutMapping("/{sessionId}/status")
    public ApiResponse<DialogueWorkItemView> transition(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WorkStatusRequest request) {
        return ApiResponse.success(workItems.transition(sessionId, request.status(), actorId(jwt)));
    }

    /** Changes queue priority without changing the conversation lifecycle. */
    @PutMapping("/{sessionId}/priority")
    public ApiResponse<DialogueWorkItemView> changePriority(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PriorityRequest request) {
        return ApiResponse.success(workItems.changePriority(sessionId, request.priority(), actorId(jwt)));
    }

    private static UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("accountId"));
    }
}
