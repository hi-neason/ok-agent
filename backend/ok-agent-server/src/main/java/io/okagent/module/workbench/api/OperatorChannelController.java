package io.okagent.module.workbench.api;

import io.okagent.module.channel.application.ChannelOperatorService;
import io.okagent.module.workbench.application.*;
import io.okagent.shared.api.Response;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workbench/operator")
public class OperatorChannelController {
    private final ChannelOperatorService service;

    public OperatorChannelController(ChannelOperatorService service) {
        this.service = service;
    }

    /** Lists channel accounts assigned to the authenticated human operator. */
    @GetMapping("/channels")
    public Response<List<MyChannelResponse>> myChannels(@AuthenticationPrincipal Jwt jwt) {
        return Response.success(service.listMyChannels(actorId(jwt)));
    }

    /** Returns the authenticated human operator's current availability. */
    @GetMapping("/presence")
    public Response<OperatorPresenceResponse> presence(@AuthenticationPrincipal Jwt jwt) {
        return Response.success(service.getPresence(actorId(jwt)));
    }

    /** Changes the authenticated human operator's availability for handoff routing. */
    @PutMapping("/presence")
    public Response<OperatorPresenceResponse> setPresence(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OperatorPresenceRequest request) {
        return Response.success(service.setPresence(actorId(jwt), request.status()));
    }

    private static UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("accountId"));
    }
}
