package io.okagent.module.channel.api;

import io.okagent.module.channel.application.*;
import io.okagent.module.channel.application.ChannelAssetService;
import io.okagent.module.channel.application.WechatIlinkLoginService;
import io.okagent.module.channel.application.runtime.FeishuAppRegistrationService;
import io.okagent.module.channel.application.runtime.dingtalk.DingTalkRegistrationService;
import io.okagent.module.channel.application.runtime.wechat.WechatLoginRegistrationService;
import io.okagent.shared.api.Response;
import io.okagent.shared.api.PageResponse;
import jakarta.validation.Valid;
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
    private final DingTalkRegistrationService dingtalkRegistration;

    public ChannelAssetController(
            ChannelAssetService service,
            FeishuAppRegistrationService feishuRegistration,
            WechatIlinkLoginService wechatLogin,
            WechatLoginRegistrationService wechatRegistration,
            DingTalkRegistrationService dingtalkRegistration) {
        this.service = service;
        this.feishuRegistration = feishuRegistration;
        this.wechatLogin = wechatLogin;
        this.wechatRegistration = wechatRegistration;
        this.dingtalkRegistration = dingtalkRegistration;
    }

    @GetMapping
    /** Returns channel instances configured in the management scope. */
    public Response<PageResponse<ChannelAssetResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return Response.success(PageResponse.of(service.list(page, size)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    /** Creates a new channel instance and provisions its framework channel key. */
    public Response<ChannelAssetResponse> create(@Valid @RequestBody ChannelAssetRequest request) {
        return Response.success(service.create(request));
    }

    @PutMapping("/{id}")
    /** Replaces the editable configuration of an existing channel instance. */
    public Response<ChannelAssetResponse> update(
            @PathVariable UUID id, @Valid @RequestBody ChannelAssetRequest request) {
        return Response.success(service.update(id, request));
    }

    @PatchMapping("/{id}/enabled")
    /** Enables or disables a channel for runtime activation. */
    public Response<ChannelAssetResponse> setEnabled(@PathVariable UUID id, @RequestParam boolean value) {
        return Response.success(service.setEnabled(id, value));
    }

    @PostMapping("/{id}/start")
    /** Starts the framework runtime channel for this configuration. */
    public Response<ChannelAssetResponse> start(@PathVariable UUID id) {
        return Response.success(service.start(id));
    }

    @PostMapping("/{id}/stop")
    /** Stops the framework runtime channel for this configuration. */
    public Response<ChannelAssetResponse> stop(@PathVariable UUID id) {
        return Response.success(service.stop(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    /** Deletes a channel instance and stops its runtime if active. */
    public Response<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return Response.success(null);
    }

    // ---------- Feishu one-click app creation (scan QR) ----------

    @PostMapping("/feishu/register/start")
    /** Starts a Feishu "create app in one click" device-auth flow; returns a session to poll. */
    public Response<FeishuAppRegistrationService.StartedSession> startFeishuRegistration() {
        return Response.success(feishuRegistration.start());
    }

    @GetMapping("/feishu/register/{sessionId}")
    /** Polls a Feishu registration flow; on SUCCESS carries the created app's id/secret. */
    public Response<FeishuAppRegistrationService.SessionStatus> feishuRegistrationStatus(
            @PathVariable String sessionId) {
        return Response.success(feishuRegistration.status(sessionId));
    }

    // ---------- WeChat iLink (ClawBot) independent QR registration (before channel exists) ----------

    @PostMapping("/wechat/register/start")
    /**
     * Starts a WeChat iLink QR-login flow independent of any channel (mirrors the Feishu flow).
     * Returns a loginId to poll; the (encrypted) bot_token is claimed when the channel is saved.
     */
    public Response<WechatLoginRegistrationService.StartedSession> startWechatRegistration(
            @RequestBody(required = false) WechatLoginRegistrationService.StartRequest request) {
        return Response.success(wechatRegistration.start(
                request != null ? request : new WechatLoginRegistrationService.StartRequest(null, null)));
    }

    @GetMapping("/wechat/register/{loginId}")
    /** Polls a WeChat registration flow; on SUCCESS carries the scanned bot's id/userId (no token). */
    public Response<WechatLoginRegistrationService.SessionStatus> wechatRegistrationStatus(
            @PathVariable String loginId) {
        return Response.success(wechatRegistration.status(loginId));
    }

    // ---------- DingTalk scan-QR to create/bind a robot (before channel exists) ----------

    @PostMapping("/dingtalk/register/start")
    /**
     * Starts a DingTalk device-authorization (scan QR) flow independent of any channel. The
     * response carries the verification URL (rendered as a QR code) and a loginId to poll. On
     * confirmation the AppKey/AppSecret are claimed when the channel is saved.
     */
    public Response<DingTalkRegistrationService.StartedSession> startDingTalkRegistration() {
        return Response.success(dingtalkRegistration.start());
    }

    @GetMapping("/dingtalk/register/{loginId}")
    /** Polls a DingTalk registration flow; on SUCCESS carries the scanned AppKey (secret is held server-side). */
    public Response<DingTalkRegistrationService.SessionStatus> dingTalkRegistrationStatus(
            @PathVariable String loginId) {
        return Response.success(dingtalkRegistration.status(loginId));
    }

    // ---------- WeChat iLink (ClawBot) QR login (per existing channel) ----------

    @PostMapping("/{id}/wechat/login/start")
    /** Issues a WeChat iLink login QR code; the response carries the qrcodeUrl to render. */
    public Response<WechatIlinkStatusResponse> startWechatLogin(@PathVariable UUID id) {
        return Response.success(wechatLogin.startLogin(id));
    }

    @PostMapping("/{id}/wechat/login/poll")
    /**
     * Polls the pending QR scan status. On confirmation it stores the bot_token and (re)starts the
     * runtime channel.
     */
    public Response<WechatIlinkStatusResponse> pollWechatLogin(@PathVariable UUID id) {
        return Response.success(wechatLogin.pollStatus(id));
    }

    @GetMapping("/{id}/wechat/login")
    /** Returns the current WeChat iLink login status without contacting iLink. */
    public Response<WechatIlinkStatusResponse> wechatLoginStatus(@PathVariable UUID id) {
        return Response.success(wechatLogin.getStatus(id));
    }

    @PostMapping("/{id}/wechat/logout")
    /** Clears the stored iLink bot_token and stops the channel runtime. */
    public Response<WechatIlinkStatusResponse> wechatLogout(@PathVariable UUID id) {
        return Response.success(wechatLogin.logout(id));
    }
}
