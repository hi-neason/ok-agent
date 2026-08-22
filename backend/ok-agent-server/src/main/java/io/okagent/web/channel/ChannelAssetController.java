package io.okagent.web.channel;

import io.okagent.service.channel.ChannelAssetService;
import io.okagent.service.channel.WechatIlinkLoginService;
import io.okagent.service.channel.runtime.FeishuAppRegistrationService;
import io.okagent.service.channel.runtime.wechat.WechatLoginRegistrationService;
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
    private final WechatIlinkLoginService wechatLogin;
    private final WechatLoginRegistrationService wechatRegistration;

    public ChannelAssetController(
            ChannelAssetService service,
            FeishuAppRegistrationService feishuRegistration,
            WechatIlinkLoginService wechatLogin,
            WechatLoginRegistrationService wechatRegistration) {
        this.service = service;
        this.feishuRegistration = feishuRegistration;
        this.wechatLogin = wechatLogin;
        this.wechatRegistration = wechatRegistration;
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

    // ---------- WeChat iLink (ClawBot) independent QR registration (before channel exists) ----------

    @PostMapping("/wechat/register/start")
    /**
     * Starts a WeChat iLink QR-login flow independent of any channel (mirrors the Feishu flow).
     * Returns a loginId to poll; the (encrypted) bot_token is claimed when the channel is saved.
     */
    public WechatLoginRegistrationService.StartedSession startWechatRegistration(
            @RequestBody(required = false) WechatLoginRegistrationService.StartRequest request) {
        return wechatRegistration.start(request != null ? request : new WechatLoginRegistrationService.StartRequest(null, null));
    }

    @GetMapping("/wechat/register/{loginId}")
    /** Polls a WeChat registration flow; on SUCCESS carries the scanned bot's id/userId (no token). */
    public WechatLoginRegistrationService.SessionStatus wechatRegistrationStatus(@PathVariable String loginId) {
        return wechatRegistration.status(loginId);
    }

    // ---------- WeChat iLink (ClawBot) QR login (per existing channel) ----------

    @PostMapping("/{id}/wechat/login/start")
    /** Issues a WeChat iLink login QR code; the response carries the qrcodeUrl to render. */
    public WechatIlinkStatusResponse startWechatLogin(@PathVariable UUID id) {
        return wechatLogin.startLogin(id);
    }

    @PostMapping("/{id}/wechat/login/poll")
    /**
     * Polls the pending QR scan status. On confirmation it stores the bot_token and (re)starts the
     * runtime channel.
     */
    public WechatIlinkStatusResponse pollWechatLogin(@PathVariable UUID id) {
        return wechatLogin.pollStatus(id);
    }

    @GetMapping("/{id}/wechat/login")
    /** Returns the current WeChat iLink login status without contacting iLink. */
    public WechatIlinkStatusResponse wechatLoginStatus(@PathVariable UUID id) {
        return wechatLogin.getStatus(id);
    }

    @PostMapping("/{id}/wechat/logout")
    /** Clears the stored iLink bot_token and stops the channel runtime. */
    public WechatIlinkStatusResponse wechatLogout(@PathVariable UUID id) {
        return wechatLogin.logout(id);
    }
}
