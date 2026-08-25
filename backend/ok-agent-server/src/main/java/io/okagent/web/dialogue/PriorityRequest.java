package io.okagent.web.dialogue;

import io.okagent.domain.dialogue.DialoguePriority;
import jakarta.validation.constraints.NotNull;

/** Requested queue priority for a conversation. */
public record PriorityRequest(@NotNull DialoguePriority priority) {}
