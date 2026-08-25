package io.okagent.module.workbench.api;

import java.util.UUID;

/** Explicit assignee update; a null account id returns the conversation to the unassigned queue. */
public record AssignmentRequest(UUID assigneeAccountId) {}
