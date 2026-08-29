package io.okagent.module.observe.api;

import io.okagent.module.observe.application.*;

import io.okagent.module.conversation.domain.DialogueTurn;
import io.okagent.module.conversation.application.DialogueQuery;
import io.okagent.module.conversation.application.DialogueService;
import io.okagent.module.conversation.application.DialogueSummary;
import io.okagent.shared.api.PageResponse;
import io.okagent.module.observe.application.TraceService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only surface for runtime observability. It never writes; it queries the shared
 * {@link DialogueService} so the same conversation history is visible regardless of which producer
 * (debug runtime or a real runtime instance) created it.
 */
@RestController
@RequestMapping("/api/v1/observe")
public class AgentObserveController {

    private final DialogueService dialogue;
    private final TraceService traces;

    public AgentObserveController(DialogueService dialogue, TraceService traces) {
        this.dialogue = dialogue;
        this.traces = traces;
    }

    /**
     * Lists conversation sessions across all agents, filtered by optional session id, user id,
     * agent id, and a created-at time range. Used by the "运行观测" history list.
     */
    @GetMapping("/sessions")
    public PageResponse<DialogueSummary> listSessions(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) UUID agentId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(dialogue.search(new DialogueQuery(sessionId, userId, agentId, from, to), page, size));
    }

    /** Returns the full, ordered conversation of a session for the detail / replay view. */
    @GetMapping("/sessions/{sessionId}/turns")
    public List<DialogueTurn> getTurns(@PathVariable String sessionId) {
        return dialogue.getMessages(sessionId);
    }

    /**
     * Returns the execution trace (ordered agent/model/tool spans) for one dialogue turn, so the
     * observability UI can expand a turn into its ReAct chain: model calls, MCP/knowledge/workflow
     * tool executions, token usage, timings and full inputs/outputs.
     */
    @GetMapping("/traces/{traceId}")
    public List<TraceSpanResponse> getTrace(@PathVariable String traceId) {
        return traces.findTrace(traceId)
                .map(spans -> spans.stream().map(TraceSpanResponse::from).toList())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace not found"));
    }
}
