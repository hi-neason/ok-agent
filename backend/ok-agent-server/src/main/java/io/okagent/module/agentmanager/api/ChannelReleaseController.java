package io.okagent.module.agentmanager.api;

import io.okagent.module.agentmanager.application.ReleaseService;
import io.okagent.shared.api.Response;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Channel-scoped release endpoints: the version currently serving a channel, its full release
 * history, and one-click rollback to the previous release.
 */
@RestController
@RequestMapping("/api/v1/channels/{channelId}")
public class ChannelReleaseController {

    private final ReleaseService releases;

    public ChannelReleaseController(ReleaseService releases) {
        this.releases = releases;
    }

    /** Returns the release currently promoted on the channel, or 204 when none is published. */
    @GetMapping("/current-release")
    public Response<ReleaseResponse> currentRelease(@PathVariable UUID channelId) {
        var current = releases.getCurrentRelease(channelId);
        if (current == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NO_CONTENT);
        }
        return Response.success(ReleaseResponse.from(current));
    }

    /** Rolls the channel back to its previous release, marking the current one ROLLED_BACK. */
    @PostMapping("/rollback")
    @ResponseStatus(HttpStatus.CREATED)
    public Response<ReleaseResponse> rollback(
            @PathVariable UUID channelId, @RequestHeader(value = "X-Actor", required = false) String actor) {
        return Response.success(ReleaseResponse.from(releases.rollbackChannel(channelId, actor)));
    }

    /** Returns the release history for the channel, newest first. */
    @GetMapping("/releases")
    public Response<List<ReleaseResponse>> channelReleases(@PathVariable UUID channelId) {
        return Response.success(releases.listChannelReleases(channelId).stream()
                .map(ReleaseResponse::from)
                .toList());
    }
}
