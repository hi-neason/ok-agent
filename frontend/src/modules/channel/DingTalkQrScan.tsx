import { useEffect, useRef, useState } from "react";
import QRCode from "qrcode";
import { useTranslation } from "react-i18next";
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
  const { t } = useTranslation();
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
      setError(e instanceof Error ? e.message : t("qr.startFailed"));
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
      setError(t("qr.expiredError"));
      return;
    }
    if (s.state === "FAILED") {
      clearTimers();
      setPhase("error");
      setError(s.error || t("qr.dingtalk.authorizeFailed"));
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
      setError(e instanceof Error ? e.message : t("qr.pollFailed"));
      setPhase("error");
    }
  };

  if (phase === "idle") {
    return (
      <button type="button" className="feishu-qr-trigger" onClick={() => void begin()}>
        <span className="feishu-qr-icon">▣</span>
        {t("qr.dingtalk.trigger")}
      </button>
    );
  }

  return (
    <div className="feishu-qr-panel">
      <div className="feishu-qr-head">
        <b>{t("qr.dingtalk.title")}</b>
        {(phase === "loading" || phase === "show") && (
          <button
            type="button"
            className="link-button"
            onClick={() => {
              clearTimers();
              setPhase("idle");
            }}
          >
            {t("qr.cancel")}
          </button>
        )}
      </div>

      {phase === "loading" && <div className="feishu-qr-loading">{t("qr.generating")}</div>}

      {(phase === "show" || phase === "expired") && qrSvg && (
        <div className={`feishu-qr-body ${phase === "expired" ? "is-expired" : ""}`}>
          <span
            className="feishu-qr-img"
            dangerouslySetInnerHTML={{ __html: qrSvg ?? "" }}
          />
          {phase === "expired" && (
            <div className="feishu-qr-mask">
              <span>{t("qr.expired")}</span>
              <button type="button" className="link-button" onClick={() => void begin()}>
                {t("qr.regenerate")}
              </button>
            </div>
          )}
        </div>
      )}

      {phase === "show" && (
        <div className="feishu-qr-tip">
          {t("qr.dingtalk.tip")}
          {secondsLeft > 0 && <span className="feishu-qr-ttl">{t("qr.ttl", { seconds: secondsLeft })}</span>}
        </div>
      )}

      {phase === "success" && (
        <div className="feishu-qr-ok">✓ {t("qr.ready")}</div>
      )}

      {(phase === "error" || phase === "expired") && error && (
        <div className="feishu-qr-err">
          × {error}
          {phase === "error" && (
            <div style={{ marginTop: 8 }}>
              <button type="button" className="link-button" onClick={() => void begin()}>
                {t("qr.retry")}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
