package io.okagent.module.workbench.api;

import io.okagent.domain.dialogue.DialogueWorkStatus;
import jakarta.validation.constraints.NotNull;

/** Requested operational state transition for a conversation. */
public record WorkStatusRequest(@NotNull DialogueWorkStatus status) {}
