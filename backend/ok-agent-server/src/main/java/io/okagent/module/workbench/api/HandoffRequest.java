package io.okagent.module.workbench.api;

import io.okagent.module.conversation.domain.DialoguePriority;

/** Optional priority supplied when a conversation requests human handling. */
public record HandoffRequest(DialoguePriority priority) {}
