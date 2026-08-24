import { useCallback, useEffect, useRef, useState } from "react";
import QRCode from "qrcode";
import { useTranslation } from "react-i18next";
import {
  fetchWechatLogin,
  pollWechatLogin,
  startWechatLogin,
  wechatLogout,
} from "./api";
import type { WechatIlinkStatus } from "./types";
import "./channel.css";

type Props = {
  /** Channel id. Null in create mode before the temp channel is persisted. */
  channelId: string | null;
  /** Automatically request a QR code once channelId is available. Default true. */
  autoStart?: boolean;
  /** Called when the user clicks 取消 during an in-progress scan. */
  onCancel?: () => void;
};

type Phase =
  | "idle"
  | "loading"
  | "starting"
  | "show"
  | "scanned"
  | "logged-in"
  | "expired"
  | "error";

/** True only for an inline data-URI image we can drop directly into an <img>. */
function isDataImageUri(value: string | null | undefined): boolean {
  return !!value && value.trim().startsWith("data:image/");
}

/**
 * WeChat personal-account (iLink / ClawBot) QR login panel.
 *
 * The iLink endpoint returns two fields:
 *  - qrcodeToken: the polling identifier (NOT what gets scanned);
 *  - qrcodeUrl: the `qrcode_img_content` — either an image URL/data-URI or the
 *    raw QR payload string to encode. We render both cases transparently.
 *
 * In create mode the channel is persisted silently before mounting this
 * component, so channelId may be null briefly. The scan is auto-started once
 * the id resolves. Cancelling calls onCancel so the parent can delete the
 * temporary channel.
 */
export function WechatQrLogin({ channelId, autoStart = true, onCancel }: Props) {
  const { t, i18n } = useTranslation();
  const [phase, setPhase] = useState<Phase>("idle");
  const [status, setStatus] = useState<WechatIlinkStatus | null>(null);
  const [qrSvg, setQrSvg] = useState<string | null>(null);
  const [error, setError] = useState<string>("");
  const pollTimer = useRef<number | null>(null);
  const startedFor = useRef<string | null>(null);

  const clearTimer = () => {
    if (pollTimer.current) {
      window.clearTimeout(pollTimer.current);
      pollTimer.current = null;
    }
  };

  useEffect(() => () => clearTimer(), []);

  const renderQr = useCallback(async (raw: string | null | undefined) => {
    if (!raw) {
      setQrSvg(null);
      return;
    }
    // Only an inline data-URI image can be shown directly. Any other value
    // (https URL, raw token, etc.) is the QR *payload* and must be encoded.
    if (isDataImageUri(raw)) {
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
  }, []);

  const applyStatus = useCallback(
    (s: WechatIlinkStatus) => {
      setStatus(s);
      switch (s.loginStatus) {
        case "LOGGED_IN":
          clearTimer();
          setPhase("logged-in");
          break;
        case "SCANNED":
          setPhase("scanned");
          break;
        case "EXPIRED":
          clearTimer();
          setPhase("expired");
          break;
        case "ERROR":
          clearTimer();
          setPhase("error");
          setError(s.lastError || t("qr.wechat.loginError"));
          break;
        case "WAITING_QR":
          setPhase("show");
          void renderQr(s.qrcodeUrl);
          break;
        case "LOGGED_OUT":
        default:
          setPhase("idle");
      }
    },
    [renderQr, t],
  );

  const refresh = useCallback(
    async (id: string) => {
      setPhase("loading");
      try {
        const s = await fetchWechatLogin(id);
        applyStatus(s);
      } catch (e) {
        setPhase("error");
        setError(e instanceof Error ? e.message : t("qr.wechat.statusLoadFailed"));
      }
    },
    [applyStatus, t],
  );

  const begin = useCallback(
    async (id: string) => {
      setPhase("starting");
      setError("");
      setQrSvg(null);
      clearTimer();
      startedFor.current = id;
      try {
        const s = await startWechatLogin(id);
        applyStatus(s);
      } catch (e) {
        setPhase("error");
        setError(e instanceof Error ? e.message : t("qr.wechat.startLoginFailed"));
      }
    },
    [applyStatus, t],
  );

  // Load existing status (or auto-start) whenever channelId changes.
  useEffect(() => {
    if (!channelId) {
      setPhase("idle");
      return;
    }
    void (async () => {
      try {
        const existing = await fetchWechatLogin(channelId);
        if (
          existing.loginStatus === "LOGGED_IN" ||
          existing.loginStatus === "SCANNED" ||
          existing.loginStatus === "WAITING_QR"
        ) {
          applyStatus(existing);
        } else if (autoStart) {
          await begin(channelId);
        } else {
          applyStatus(existing);
        }
      } catch {
        if (autoStart) {
          await begin(channelId);
        } else {
          setPhase("idle");
        }
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [channelId]);

  // Poll loop while waiting for scan / confirmation.
  useEffect(() => {
    if (!channelId) return;
    if (phase !== "show" && phase !== "scanned") return;
    pollTimer.current = window.setTimeout(() => {
      void (async () => {
        try {
          const s = await pollWechatLogin(channelId);
          applyStatus(s);
        } catch (e) {
          setError(e instanceof Error ? e.message : t("qr.pollFailed"));
          pollTimer.current = window.setTimeout(() => {
            if (!channelId) return;
            void pollWechatLogin(channelId)
              .then(applyStatus)
              .catch(() => setPhase("error"));
          }, 3000);
        }
      })();
    }, 2000);
    return clearTimer;
  }, [phase, channelId, applyStatus, t]);

  const logout = useCallback(async () => {
    if (!channelId) return;
    try {
      const s = await wechatLogout(channelId);
      applyStatus(s);
    } catch (e) {
      setError(e instanceof Error ? e.message : t("qr.wechat.logoutFailed"));
    }
  }, [channelId, applyStatus, t]);

  const cancelScan = () => {
    clearTimer();
    setPhase("idle");
    setStatus(null);
    setQrSvg(null);
    startedFor.current = null;
    onCancel?.();
  };

  const showQr =
    (phase === "show" || phase === "expired") &&
    status?.qrcodeUrl &&
    isDataImageUri(status.qrcodeUrl)
      ? "image"
      : (phase === "show" || phase === "expired") && qrSvg
        ? "svg"
        : null;

  return (
    <div className="feishu-qr-panel">
      <div className="feishu-qr-head">
        <b>{t("qr.wechat.title")}</b>
        {phase === "logged-in" && (
          <button
            type="button"
            className="link-button"
            onClick={() => void logout()}
          >
            {t("qr.wechat.logout")}
          </button>
        )}
        {(phase === "show" ||
          phase === "scanned" ||
          phase === "starting" ||
          phase === "expired") &&
          onCancel && (
            <button type="button" className="link-button" onClick={cancelScan}>
              {t("qr.cancel")}
            </button>
          )}
      </div>

      {!channelId && (
        <div className="feishu-qr-loading">{t("qr.wechat.preparing")}</div>
      )}

      {channelId && phase === "loading" && (
        <div className="feishu-qr-loading">{t("qr.wechat.loadingStatus")}</div>
      )}

      {channelId && phase === "starting" && (
        <div className="feishu-qr-loading">{t("qr.wechat.generatingLogin")}</div>
      )}

      {channelId && showQr === "image" && status?.qrcodeUrl && (
        <div
          className={`feishu-qr-body ${phase === "expired" ? "is-expired" : ""}`}
        >
          <img
            className="feishu-qr-img-tag"
            src={status.qrcodeUrl}
            alt={t("qr.wechat.alt")}
          />
          {phase === "expired" && (
            <div className="feishu-qr-mask">
              <span>{t("qr.expired")}</span>
              <button
                type="button"
                className="link-button"
                onClick={() => void begin(channelId)}
              >
                {t("qr.regenerate")}
              </button>
            </div>
          )}
        </div>
      )}

      {channelId && showQr === "svg" && (
        <div
          className={`feishu-qr-body ${phase === "expired" ? "is-expired" : ""}`}
        >
          <span
            className="feishu-qr-img"
            dangerouslySetInnerHTML={{ __html: qrSvg ?? "" }}
          />
          {phase === "expired" && (
            <div className="feishu-qr-mask">
              <span>{t("qr.expired")}</span>
              <button
                type="button"
                className="link-button"
                onClick={() => void begin(channelId)}
              >
                {t("qr.regenerate")}
              </button>
            </div>
          )}
        </div>
      )}

      {channelId && phase === "show" && (
        <div className="feishu-qr-tip">
          {t("qr.wechat.forwardingTip")}
        </div>
      )}

      {channelId && phase === "scanned" && (
        <div className="feishu-qr-ok">
          ✓ {t("qr.wechat.scanned")}
        </div>
      )}

      {channelId && phase === "logged-in" && status && (
        <div className="feishu-qr-ok">
          <div>
            ✓ {t("qr.wechat.loggedIn")}
            {status.botId ? ` · ${t("qr.wechat.botId", { id: status.botId })}` : ""}
            {status.ilinkUserId ? ` · ${t("qr.wechat.userId", { id: status.ilinkUserId })}` : ""}
          </div>
          {status.loggedInAt && (
            <small>
              {t("qr.wechat.loginTime", {
                time: new Date(status.loggedInAt).toLocaleString(i18n.resolvedLanguage),
              })}
            </small>
          )}
        </div>
      )}

      {channelId && phase === "idle" && (
        <div className="feishu-qr-tip">
          {t("qr.wechat.loggedOut")}
          <div style={{ marginTop: 8 }}>
            <button
              type="button"
              className="feishu-qr-trigger"
              onClick={() => void begin(channelId)}
            >
              <span className="feishu-qr-icon">▣</span>
              {t("qr.wechat.login")}
            </button>
          </div>
        </div>
      )}

      {channelId && phase === "expired" && (
        <div className="feishu-qr-tip">
          {t("qr.expiredError")}
          <div style={{ marginTop: 8 }}>
            <button
              type="button"
              className="feishu-qr-trigger"
              onClick={() => void begin(channelId)}
            >
              <span className="feishu-qr-icon">▣</span>
              {t("qr.wechat.rescan")}
            </button>
          </div>
        </div>
      )}

      {channelId && phase === "error" && (
        <div className="feishu-qr-err">
          × {error || t("qr.wechat.genericError")}
          <div style={{ marginTop: 8 }}>
            <button
              type="button"
              className="link-button"
              onClick={() => void begin(channelId)}
            >
              {t("qr.retry")}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
