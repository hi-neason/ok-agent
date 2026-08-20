package io.okagent.web.channel;

import io.okagent.service.channel.ChannelAssetService;
import io.okagent.service.channel.runtime.FeishuAppRegistrationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/channels")
public class ChannelAssetController {

    private final ChannelAssetService service;
    private final FeishuAppRegistrationService feishuRegistration;

    public ChannelAssetController(ChannelAssetService service, FeishuAppRegistrationService feishuRegistration) {
        this.service = service;
        this.feishuRegistration = feishuRegistration;
    }

    @GetMapping
    /** Returns all channel instances configured in the management scope. */
    public List<ChannelAssetResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    /** Creates a new channel instance and provisions its framework channel key. */
    public ChannelAssetResponse create(@Valid @RequestBody ChannelAssetRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    /** Replaces the editable configuration of an existing channel instance. */
    public ChannelAssetResponse update(@PathVariable UUID id, @Valid @RequestBody ChannelAssetRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/enabled")
    /** Enables or disables a channel for runtime activation. */
    public ChannelAssetResponse setEnabled(@PathVariable UUID id, @RequestParam boolean value) {
        return service.setEnabled(id, value);
    }

    @PostMapping("/{id}/start")
    /** Starts the framework runtime channel for this configuration. */
    public ChannelAssetResponse start(@PathVariable UUID id) {
        return service.start(id);
    }

    @PostMapping("/{id}/stop")
    /** Stops the framework runtime channel for this configuration. */
    public ChannelAssetResponse stop(@PathVariable UUID id) {
        return service.stop(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    /** Deletes a channel instance and stops its runtime if active. */
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    // ---------- Feishu one-click app creation (scan QR) ----------

    @PostMapping("/feishu/register/start")
    /** Starts a Feishu "create app in one click" device-auth flow; returns a session to poll. */
    public FeishuAppRegistrationService.StartedSession startFeishuRegistration() {
        return feishuRegistration.start();
    }

    @GetMapping("/feishu/register/{sessionId}")
    /** Polls a Feishu registration flow; on SUCCESS carries the created app's id/secret. */
    public FeishuAppRegistrationService.SessionStatus feishuRegistrationStatus(@PathVariable String sessionId) {
        return feishuRegistration.status(sessionId);
    }
}
