package io.okagent.web.release;

import io.okagent.domain.release.AgentRelease;
import io.okagent.service.release.ReleaseService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Version and release endpoints scoped to an agent. Versions are immutable snapshots created from
 * the agent's draft; a release deploys one version onto a channel.
 */
@RestController
@RequestMapping("/api/v1/agents/{agentId}")
public class AgentReleaseController {

    private final ReleaseService releases;

    public AgentReleaseController(ReleaseService releases) {
        this.releases = releases;
    }

    /** Lists all versions of the agent, newest first (without snapshot payloads). */
    @GetMapping("/versions")
    public List<VersionResponse> listVersions(@PathVariable UUID agentId) {
        return releases.listVersions(agentId).stream().map(VersionResponse::from).toList();
    }

    /** Creates a new immutable version from the agent's current draft. */
    @PostMapping("/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public VersionDetailResponse createVersion(
            @PathVariable UUID agentId,
            @Valid @RequestBody CreateVersionRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        var version = releases.createVersion(agentId, request.label(), request.changelog(), actor);
        return VersionDetailResponse.from(version);
    }

    /** Returns a single version including its full snapshot JSON. */
    @GetMapping("/versions/{versionId}")
    public VersionDetailResponse getVersion(
            @PathVariable UUID agentId, @PathVariable UUID versionId) {
        return VersionDetailResponse.from(releases.getVersion(versionId));
    }

    /** Publishes a version of the agent onto a channel, superseding the channel's current release. */
    @PostMapping("/releases")
    @ResponseStatus(HttpStatus.CREATED)
    public ReleaseResponse publish(
            @PathVariable UUID agentId,
            @Valid @RequestBody PublishReleaseRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        AgentRelease release =
                releases.publishToChannel(agentId, request.versionNo(), request.channelId(), actor);
        return ReleaseResponse.from(release);
    }

    /** Returns the release history for this agent across all channels, newest first. */
    @GetMapping("/releases")
    public List<ReleaseResponse> listReleases(@PathVariable UUID agentId) {
        return releases.listAgentReleases(agentId).stream().map(ReleaseResponse::from).toList();
    }
}
