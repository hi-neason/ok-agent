package io.okagent.service.release;

import io.okagent.domain.release.AgentRelease;
import io.okagent.domain.release.AgentVersion;
import java.util.List;
import java.util.UUID;

/**
 * Version and release lifecycle for agents. A version is an immutable frozen snapshot; a release
 * deploys a version onto a channel. Implementations must run publish/rollback state transitions
 * transactionally so that a channel's current-release pointer and the release status stay
 * consistent.
 */
public interface ReleaseService {

    /** Creates an immutable version from the agent's current draft. */
    AgentVersion createVersion(UUID agentId, String label, String changelog, String actor);

    /** Lists all versions of an agent, newest first. */
    List<AgentVersion> listVersions(UUID agentId);

    /** Loads a single version. */
    AgentVersion getVersion(UUID versionId);

    /** Promotes a version onto a channel, superseding the channel's current release. */
    AgentRelease publishToChannel(UUID agentId, int versionNo, UUID channelId, String actor);

    /** Rolls a channel back to its previous release, marking the current one ROLLED_BACK. */
    AgentRelease rollbackChannel(UUID channelId, String actor);

    /** Returns the release currently promoted on a channel, or empty if none. */
    AgentRelease getCurrentRelease(UUID channelId);

    /** Returns the release history for a channel, newest first. */
    List<AgentRelease> listChannelReleases(UUID channelId);

    /** Returns every release of an agent across all targets, newest first. */
    List<AgentRelease> listAgentReleases(UUID agentId);
}
