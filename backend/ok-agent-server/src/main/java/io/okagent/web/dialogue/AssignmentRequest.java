package io.okagent.web.dialogue;

import java.util.UUID;

/** Explicit assignee update; a null account id returns the conversation to the unassigned queue. */
public record AssignmentRequest(UUID assigneeAccountId) {}
