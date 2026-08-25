package io.okagent.module.workbench.api;

import io.okagent.domain.dialogue.DialoguePriority;

/** Optional priority supplied when a conversation requests human handling. */
public record HandoffRequest(DialoguePriority priority) {}
