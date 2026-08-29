package io.okagent.module.channel.api;

import io.okagent.module.channel.application.*;

import io.okagent.module.channel.application.ChannelOperatorService;
import io.okagent.module.identity.application.AuthenticatedActor;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/channels/{channelId}/operators")
public class ChannelOperatorController {
    private final ChannelOperatorService service;

    public ChannelOperatorController(ChannelOperatorService service) {
        this.service = service;
    }

    /** Lists all eligible human operators and their assignment state for a channel. */
    @GetMapping
    public List<ChannelOperatorResponse> list(@PathVariable UUID channelId) {
        return service.listOperators(channelId);
    }

    /** Replaces the channel's complete human operator assignment set. */
    @PutMapping
    public List<ChannelOperatorResponse> replace(
            @PathVariable UUID channelId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChannelOperatorAssignmentRequest request) {
        return service.replaceAssignments(channelId, request.operatorAccountIds(), actor(jwt));
    }

    private static AuthenticatedActor actor(Jwt jwt) {
        return new AuthenticatedActor(
                UUID.fromString(jwt.getClaimAsString("accountId")), jwt.getClaimAsString("username"));
    }
}
