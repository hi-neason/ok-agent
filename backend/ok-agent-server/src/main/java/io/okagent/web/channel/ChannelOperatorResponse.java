package io.okagent.web.channel;

import io.okagent.domain.user.AccountRole;
import java.util.UUID;

/** Assignable console operator and whether the channel currently grants access. */
public record ChannelOperatorResponse(
        UUID accountId, String username, String displayName, AccountRole role, boolean assigned) {}
