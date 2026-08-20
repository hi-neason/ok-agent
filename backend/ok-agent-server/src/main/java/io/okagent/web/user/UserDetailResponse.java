package io.okagent.web.user;

import java.util.List;

/**
 * Aggregated view of one user for the detail page: the basic profile, the provider identities
 * (Feishu open_id, etc.) it aggregates under, and a few life-cycle counts.
 */
public record UserDetailResponse(
        UserResponse user,
        List<ChannelIdentityView> channels,
        long sessionCount,
        long personaCount,
        long traceCount,
        long messageCount) {}
