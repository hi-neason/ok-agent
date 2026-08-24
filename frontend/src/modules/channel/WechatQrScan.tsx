import { useEffect, useRef, useState } from "react";
import QRCode from "qrcode";
import { useTranslation } from "react-i18next";
import {
  pollWechatRegistration,
  startWechatRegistration,
  type WechatRegisterStatus,
} from "./api";
import "./channel.css";

type Props = {
  /** Optional overrides typed into the form (apiBase / channelVersion). */
  apiBase?: string;
  channelVersion?: string;
  /** Called once the scan is confirmed, handing back the loginId to persist. */
  onSuccess: (loginId: string, info: { botId: string | null; ilinkUserId: string | null }) => void;
};

type Phase = "idle" | "loading" | "show" | "scanned" | "success" | "error" | "expired";

/** True only for an inline data-URI image we can drop directly into an <img>. */
function isDataImageUri(value: string | null | undefined): boolean {
  return !!value && value.trim().startsWith("data:image/");
}

/**
 * WeChat personal-account (iLink / ClawBot) QR scan for the CREATE flow, independent of any
 * channel row (mirrors {@link FeishuQrScan}). It starts a backend registration session, gets a
 * loginId, polls until the user confirms on the phone, then hands the loginId back so the parent
 * includes it on the create-channel request. No channel exists until save — canceling leaves
 * nothing to clean up.
 */
export function WechatQrScan({ apiBase, channelVersion, onSuccess }: Props) {
  const { t } = useTranslation();
  const [phase, setPhase] = useState<Phase>("idle");
  const [qrSvg, setQrSvg] = useState<string | null>(null);
  const [qrImage, setQrImage] = useState<string | null>(null);
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
      setQrImage(null);
      return;
    }
    if (isDataImageUri(raw)) {
      setQrSvg(null);
      setQrImage(raw);
      return;
    }
    try {
      const svg = await QRCode.toString(raw, {
        type: "svg",
        margin: 1,
        color: { dark: "#1a2b45", light: "#ffffff" },
      });
      setQrSvg(svg);
      setQrImage(null);
    } catch (qrErr) {
      setQrSvg(null);
      setQrImage(null);
      console.warn("二维码渲染失败", qrErr);
    }
  };

  const begin = async () => {
    setPhase("loading");
    setError("");
    setQrSvg(null);
    setQrImage(null);
    qrRendered.current = false;
    clearTimers();
    const base = apiBase?.trim() || undefined;
    const ver = channelVersion?.trim() || undefined;
    try {
      const { loginId } = await startWechatRegistration(base, ver);
      void poll(loginId);
    } catch (e) {
      setError(e instanceof Error ? e.message : t("qr.startFailed"));
      setPhase("error");
    }
  };

  const apply = async (s: WechatRegisterStatus, loginId: string) => {
    if (s.qrcodePayload && !qrRendered.current) {
      qrRendered.current = true;
      await renderQr(s.qrcodePayload);
    }
    if (s.expireAt) {
      const left = Math.max(0, s.expireAt - Math.floor(Date.now() / 1000));
      setSecondsLeft(left);
      if (!countdownTimer.current) {
        countdownTimer.current = window.setInterval(() => {
          setSecondsLeft((cur) => (cur <= 1 ? 0 : cur - 1));
        }, 1000);
      }
    }

    if (s.state === "SUCCESS") {
      clearTimers();
      setPhase("success");
      onSuccess(loginId, { botId: s.botId, ilinkUserId: s.ilinkUserId });
      return;
    }
    if (s.state === "SCANNED") {
      setPhase("scanned");
      pollTimer.current = window.setTimeout(() => void poll(loginId), 2000);
      return;
    }
    if (s.state === "WAITING_SCAN" || s.state === "STARTING") {
      setPhase("show");
      pollTimer.current = window.setTimeout(() => void poll(loginId), 2000);
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
      setError(s.error || t("qr.wechat.loginFailed"));
    }
  };

  const poll = async (loginId: string) => {
    try {
      const s = await pollWechatRegistration(loginId);
      await apply(s, loginId);
    } catch (e) {
      setError(e instanceof Error ? e.message : t("qr.pollFailed"));
      setPhase("error");
    }
  };

  if (phase === "idle") {
    return (
      <button type="button" className="feishu-qr-trigger" onClick={() => void begin()}>
        <span className="feishu-qr-icon">▣</span>
        {t("qr.wechat.trigger")}
      </button>
    );
  }

  return (
    <div className="feishu-qr-panel">
      <div className="feishu-qr-head">
        <b>{t("qr.wechat.title")}</b>
        {(phase === "loading" || phase === "show" || phase === "scanned") && (
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

      {(phase === "show" || phase === "scanned" || phase === "expired") && (qrSvg || qrImage) && (
        <div className={`feishu-qr-body ${phase === "expired" ? "is-expired" : ""}`}>
          {qrImage ? (
            <img className="feishu-qr-img-tag" src={qrImage} alt={t("qr.wechat.alt")} />
          ) : (
            <span
              className="feishu-qr-img"
              dangerouslySetInnerHTML={{ __html: qrSvg ?? "" }}
            />
          )}
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
          {t("qr.wechat.tip")}
          {secondsLeft > 0 && <span className="feishu-qr-ttl">{t("qr.ttl", { seconds: secondsLeft })}</span>}
        </div>
      )}

      {phase === "scanned" && (
        <div className="feishu-qr-ok">✓ {t("qr.wechat.scanned")}</div>
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
