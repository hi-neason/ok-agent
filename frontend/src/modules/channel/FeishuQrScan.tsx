import { useEffect, useRef, useState } from "react";
import QRCode from "qrcode";
import {
  pollFeishuRegistration,
  startFeishuRegistration,
} from "./api";
import "./channel.css";

type Props = {
  onSuccess: (credentials: { appId: string; appSecret: string }) => void;
};

type Phase = "idle" | "loading" | "show" | "success" | "error" | "expired";

/**
 * Feishu one-click app creation via QR scan. Calls the backend to start a device-auth
 * flow, renders the verification URL as a QR code, and polls until the user authorizes
 * in Feishu — then hands the freshly created App ID / Secret back to the parent form.
 */
export function FeishuQrScan({ onSuccess }: Props) {
  const [phase, setPhase] = useState<Phase>("idle");
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
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

  const begin = async () => {
    setPhase("loading");
    setError("");
    setQrDataUrl(null);
    qrRendered.current = false;
    clearTimers();
    try {
      const { sessionId } = await startFeishuRegistration();
      void poll(sessionId);
    } catch (e) {
      setError(e instanceof Error ? e.message : "发起扫码失败");
      setPhase("error");
    }
  };

  const poll = async (sessionId: string) => {
    try {
      const s = await pollFeishuRegistration(sessionId);

      if (s.qrUrl && !qrRendered.current) {
        qrRendered.current = true;
        const dataUrl = await QRCode.toDataURL(s.qrUrl, {
          width: 200,
          margin: 1,
          color: { dark: "#1a2b45", light: "#ffffff" },
        });
        setQrDataUrl(dataUrl);
      }

      if (s.state === "WAITING_SCAN" || s.state === "STARTING") {
        if (s.expireAt) {
          const left = Math.max(0, s.expireAt - Math.floor(Date.now() / 1000));
          setSecondsLeft(left);
          if (!countdownTimer.current) {
            countdownTimer.current = window.setInterval(() => {
              setSecondsLeft((cur) => {
                if (cur <= 1) {
                  return 0;
                }
                return cur - 1;
              });
            }, 1000);
          }
        }
        setPhase("show");
        pollTimer.current = window.setTimeout(() => void poll(sessionId), 2000);
        return;
      }

      if (s.state === "SUCCESS" && s.appId && s.appSecret) {
        clearTimers();
        setPhase("success");
        onSuccess({ appId: s.appId, appSecret: s.appSecret });
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
        setError(s.error || "扫码授权失败");
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "轮询状态失败");
      setPhase("error");
    }
  };

  if (phase === "idle") {
    return (
      <button type="button" className="feishu-qr-trigger" onClick={() => void begin()}>
        <span className="feishu-qr-icon">▣</span>
        扫码一键创建飞书应用
      </button>
    );
  }

  return (
    <div className="feishu-qr-panel">
      <div className="feishu-qr-head">
        <b>扫码创建飞书机器人</b>
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

      {(phase === "show" || phase === "expired") && qrDataUrl && (
        <div className={`feishu-qr-body ${phase === "expired" ? "is-expired" : ""}`}>
          <img src={qrDataUrl} alt="飞书扫码二维码" className="feishu-qr-img" />
          {phase === "expired" && (
            <div className="feishu-qr-mask">
              <span>已过期</span>
              <button
                type="button"
                className="link-button"
                onClick={() => void begin()}
              >
                重新生成
              </button>
            </div>
          )}
        </div>
      )}

      {phase === "show" && (
        <div className="feishu-qr-tip">
          请使用飞书 App 扫码，在手机上确认创建机器人应用。
          {secondsLeft > 0 && <span className="feishu-qr-ttl">二维码 {secondsLeft}s 后过期</span>}
        </div>
      )}

      {phase === "success" && (
        <div className="feishu-qr-ok">✓ 已获取应用凭证，已自动填入下方表单。</div>
      )}

      {phase === "error" && error && (
        <div className="feishu-qr-err">× {error}</div>
      )}
    </div>
  );
}
