package io.okagent.module.workbench.api;

import io.okagent.domain.dialogue.DialoguePriority;
import jakarta.validation.constraints.NotNull;

/** Requested queue priority for a conversation. */
public record PriorityRequest(@NotNull DialoguePriority priority) {}
