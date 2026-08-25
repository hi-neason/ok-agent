package io.okagent.web.dialogue;

import io.okagent.domain.dialogue.DialoguePriority;

/** Optional priority supplied when a conversation requests human handling. */
public record HandoffRequest(DialoguePriority priority) {}
