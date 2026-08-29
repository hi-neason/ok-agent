package io.okagent.module.workbench.application;

import io.okagent.module.identity.domain.AccountRole;
import java.util.UUID;

/** Minimal enabled-console-account projection safe for inbox assignment controls. */
public record DialogueOperatorView(UUID id, String username, String displayName, AccountRole role) {}
