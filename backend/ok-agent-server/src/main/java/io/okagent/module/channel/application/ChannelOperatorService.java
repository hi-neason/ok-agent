package io.okagent.module.channel.application;

import io.okagent.module.channel.domain.OperatorPresenceStatus;
import io.okagent.module.workbench.application.MyChannelResponse;
import io.okagent.module.workbench.application.OperatorPresenceResponse;
import io.okagent.module.identity.application.AuthenticatedActor;
import io.okagent.module.channel.application.ChannelOperatorResponse;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Manages the boundary between enterprise channel resources and human operator work access. */
public interface ChannelOperatorService {
    /** Lists eligible operators and their assignment state for one channel. */
    List<ChannelOperatorResponse> listOperators(UUID channelId);

    /** Returns the display names of operators currently responsible for one channel. */
    List<String> assignedOperatorNames(UUID channelId);

    /** Replaces every operator assignment for one channel and records the administrator action. */
    List<ChannelOperatorResponse> replaceAssignments(
            UUID channelId, Set<UUID> operatorAccountIds, AuthenticatedActor actor);

    /** Lists only channels assigned to the authenticated operator. */
    List<MyChannelResponse> listMyChannels(UUID operatorAccountId);

    /** Returns the authenticated operator's current presence, defaulting to offline. */
    OperatorPresenceResponse getPresence(UUID operatorAccountId);

    /** Changes the authenticated operator's availability after validating account eligibility. */
    OperatorPresenceResponse setPresence(UUID operatorAccountId, OperatorPresenceStatus status);
}
