package io.okagent.service.dialogue;

import io.okagent.domain.user.AccountRole;
import java.util.UUID;

/** Minimal enabled-console-account projection safe for inbox assignment controls. */
public record DialogueOperatorView(UUID id, String username, String displayName, AccountRole role) {}
