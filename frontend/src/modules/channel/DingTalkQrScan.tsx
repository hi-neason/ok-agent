import { useEffect, useRef, useState } from "react";
import QRCode from "qrcode";
import {
  pollDingTalkRegistration,
  startDingTalkRegistration,
  type DingTalkRegisterStatus,
} from "./api";
import "./channel.css";

type Props = {
  /** Called once the scan is confirmed, handing back the loginId to persist on save. */
  onSuccess: (loginId: string, info: { appKey: string | null }) => void;
};

type Phase = "idle" | "loading" | "show" | "success" | "error" | "expired";

/**
 * DingTalk "scan QR to create/bind a robot" component for the CREATE flow, independent of any
 * channel row (mirrors {@link WechatQrScan}). It starts a backend device-authorization session,
 * renders the returned {@code verification_uri_complete} as a QR code, and polls until the user
 * authorizes the robot in DingTalk. On success the AppKey/AppSecret are held server-side keyed by
 * loginId; the parent includes the loginId on the create-channel request so the credentials are
 * claimed at save time. No channel exists until save — canceling leaves nothing to clean up.
 */
export function DingTalkQrScan({ onSuccess }: Props) {
  const [phase, setPhase] = useState<Phase>("idle");
  const [qrSvg, setQrSvg] = useState<string | null>(null);
  const [error, setError] = useState<string>("");
  const [secondsLeft, setSecondsLeft] = useState(0);
  const qrRendered = useRef(false);
  const pollTimer = useRef<number | null>(null);
  const countdownTimer = useRef<number | null>(null);

  const clearTimers = () => {
    if (pollTimer.current) {
      window.clearTimeout(pollTimer.current);
      pollTimer.current = null;
    }
    if (countdownTimer.current) {
      window.clearInterval(countdownTimer.current);
      countdownTimer.current = null;
    }
  };

  useEffect(() => () => clearTimers(), []);

  const renderQr = async (raw: string | null) => {
    if (!raw) {
      setQrSvg(null);
      return;
    }
    try {
      const svg = await QRCode.toString(raw, {
        type: "svg",
        margin: 1,
        color: { dark: "#1a2b45", light: "#ffffff" },
      });
      setQrSvg(svg);
    } catch (qrErr) {
      setQrSvg(null);
      console.warn("二维码渲染失败", qrErr);
    }
  };

  const begin = async () => {
    setPhase("loading");
    setError("");
    setQrSvg(null);
    qrRendered.current = false;
    clearTimers();
    try {
      const { loginId, verificationUrl, expireAt, intervalSeconds } =
        await startDingTalkRegistration();
      const left = Math.max(0, expireAt - Math.floor(Date.now() / 1000));
      setSecondsLeft(left);
      countdownTimer.current = window.setInterval(() => {
        setSecondsLeft((cur) => (cur <= 1 ? 0 : cur - 1));
      }, 1000);
      qrRendered.current = true;
      await renderQr(verificationUrl);
      setPhase("show");
      void poll(loginId, intervalSeconds);
    } catch (e) {
      setError(e instanceof Error ? e.message : "发起扫码失败");
      setPhase("error");
    }
  };

  const schedulePoll = (loginId: string, intervalSeconds: number) => {
    const delay = Math.max(2, intervalSeconds) * 1000;
    pollTimer.current = window.setTimeout(() => void poll(loginId, intervalSeconds), delay);
  };

  const apply = (s: DingTalkRegisterStatus, loginId: string, intervalSeconds: number) => {
    if (s.state === "SUCCESS") {
      clearTimers();
      setPhase("success");
      onSuccess(loginId, { appKey: s.appKey });
      return;
    }
    if (s.state === "EXPIRED" || s.state === "NOT_FOUND") {
      clearTimers();
      setPhase("expired");
      setError("二维码已过期，请重新生成");
      return;
    }
    if (s.state === "FAILED") {
      clearTimers();
      setPhase("error");
      setError(s.error || "钉钉扫码授权失败");
      return;
    }
    // WAITING_SCAN / STARTING
    setPhase("show");
    schedulePoll(loginId, intervalSeconds);
  };

  const poll = async (loginId: string, intervalSeconds: number) => {
    try {
      const s = await pollDingTalkRegistration(loginId);
      apply(s, loginId, intervalSeconds);
    } catch (e) {
      setError(e instanceof Error ? e.message : "轮询状态失败");
      setPhase("error");
    }
  };

  if (phase === "idle") {
    return (
      <button type="button" className="feishu-qr-trigger" onClick={() => void begin()}>
        <span className="feishu-qr-icon">▣</span>
        扫码绑定钉钉机器人
      </button>
    );
  }

  return (
    <div className="feishu-qr-panel">
      <div className="feishu-qr-head">
        <b>钉钉扫码绑定机器人</b>
        {(phase === "loading" || phase === "show") && (
          <button
            type="button"
            className="link-button"
            onClick={() => {
              clearTimers();
              setPhase("idle");
            }}
          >
            取消
          </button>
        )}
      </div>

      {phase === "loading" && <div className="feishu-qr-loading">正在生成二维码…</div>}

      {(phase === "show" || phase === "expired") && qrSvg && (
        <div className={`feishu-qr-body ${phase === "expired" ? "is-expired" : ""}`}>
          <span
            className="feishu-qr-img"
            dangerouslySetInnerHTML={{ __html: qrSvg ?? "" }}
          />
          {phase === "expired" && (
            <div className="feishu-qr-mask">
              <span>已过期</span>
              <button type="button" className="link-button" onClick={() => void begin()}>
                重新生成
              </button>
            </div>
          )}
        </div>
      )}

      {phase === "show" && (
        <div className="feishu-qr-tip">
          请使用<b>钉钉 App</b> 扫码，在手机上授权创建/绑定机器人。
          {secondsLeft > 0 && <span className="feishu-qr-ttl">二维码 {secondsLeft}s 后过期</span>}
        </div>
      )}

      {phase === "success" && (
        <div className="feishu-qr-ok">✓ 已扫码确认，凭据已就绪，点击下方「保存渠道」完成创建。</div>
      )}

      {(phase === "error" || phase === "expired") && error && (
        <div className="feishu-qr-err">
          × {error}
          {phase === "error" && (
            <div style={{ marginTop: 8 }}>
              <button type="button" className="link-button" onClick={() => void begin()}>
                重试
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
