import { useCallback, useEffect, useRef, useState } from "react";
import QRCode from "qrcode";
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

const STATUS_HINT: Record<string, string> = {
  LOGGED_OUT: "尚未登录微信个人号。",
  WAITING_QR: "请使用微信扫码登录。",
  SCANNED: "已扫码，请在手机上确认登录。",
  LOGGED_IN: "已登录，渠道运行后将自动接收该微信号的私聊消息。",
  EXPIRED: "二维码已过期，请重新生成。",
  ERROR: "登录过程出现异常。",
};

/** True when the content is a remote/image URL we can drop into an <img>. */
function isImageUrl(value: string | null | undefined): boolean {
  if (!value) return false;
  const v = value.trim();
  return (
    v.startsWith("data:image/") ||
    v.startsWith("http://") ||
    v.startsWith("https://")
  );
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
    // Image URL / data-URI is displayed directly via <img>; no SVG needed.
    if (isImageUrl(raw)) {
      setQrSvg(null);
      return;
    }
    // Otherwise the field carries the raw QR payload — encode it ourselves.
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
          setError(s.lastError || "登录异常");
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
    [renderQr],
  );

  const refresh = useCallback(
    async (id: string) => {
      setPhase("loading");
      try {
        const s = await fetchWechatLogin(id);
        applyStatus(s);
      } catch (e) {
        setPhase("error");
        setError(e instanceof Error ? e.message : "加载登录状态失败");
      }
    },
    [applyStatus],
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
        setError(e instanceof Error ? e.message : "发起扫码登录失败");
      }
    },
    [applyStatus],
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
          setError(e instanceof Error ? e.message : "轮询状态失败");
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
  }, [phase, channelId, applyStatus]);

  const logout = useCallback(async () => {
    if (!channelId) return;
    try {
      const s = await wechatLogout(channelId);
      applyStatus(s);
    } catch (e) {
      setError(e instanceof Error ? e.message : "登出失败");
    }
  }, [channelId, applyStatus]);

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
    isImageUrl(status.qrcodeUrl)
      ? "image"
      : (phase === "show" || phase === "expired") && qrSvg
        ? "svg"
        : null;

  return (
    <div className="feishu-qr-panel">
      <div className="feishu-qr-head">
        <b>微信扫码登录（个人号 · ClawBot）</b>
        {phase === "logged-in" && (
          <button
            type="button"
            className="link-button"
            onClick={() => void logout()}
          >
            退出登录
          </button>
        )}
        {(phase === "show" ||
          phase === "scanned" ||
          phase === "starting" ||
          phase === "expired") &&
          onCancel && (
            <button type="button" className="link-button" onClick={cancelScan}>
              取消
            </button>
          )}
      </div>

      {!channelId && (
        <div className="feishu-qr-loading">正在准备扫码绑定…</div>
      )}

      {channelId && phase === "loading" && (
        <div className="feishu-qr-loading">正在加载登录状态…</div>
      )}

      {channelId && phase === "starting" && (
        <div className="feishu-qr-loading">正在生成登录二维码…</div>
      )}

      {channelId && showQr === "image" && status?.qrcodeUrl && (
        <div
          className={`feishu-qr-body ${phase === "expired" ? "is-expired" : ""}`}
        >
          <img
            className="feishu-qr-img-tag"
            src={status.qrcodeUrl}
            alt="微信登录二维码"
          />
          {phase === "expired" && (
            <div className="feishu-qr-mask">
              <span>已过期</span>
              <button
                type="button"
                className="link-button"
                onClick={() => void begin(channelId)}
              >
                重新生成
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
              <span>已过期</span>
              <button
                type="button"
                className="link-button"
                onClick={() => void begin(channelId)}
              >
                重新生成
              </button>
            </div>
          )}
        </div>
      )}

      {channelId && phase === "show" && (
        <div className="feishu-qr-tip">
          请使用<b>微信 App</b> 扫码，在手机上确认登录个人微信号。
          登录后该微信号收到的私聊消息将转发给所绑定的 Agent。
        </div>
      )}

      {channelId && phase === "scanned" && (
        <div className="feishu-qr-ok">
          ✓ 已扫码，请在手机上点击「确认登录」…
        </div>
      )}

      {channelId && phase === "logged-in" && status && (
        <div className="feishu-qr-ok">
          <div>
            ✓ 已登录{status.botId ? `（Bot ID：${status.botId}）` : ""}
            {status.ilinkUserId ? ` · 用户：${status.ilinkUserId}` : ""}
          </div>
          {status.loggedInAt && (
            <small>登录时间：{new Date(status.loggedInAt).toLocaleString()}</small>
          )}
        </div>
      )}

      {channelId && phase === "idle" && (
        <div className="feishu-qr-tip">
          {STATUS_HINT.LOGGED_OUT}
          <div style={{ marginTop: 8 }}>
            <button
              type="button"
              className="feishu-qr-trigger"
              onClick={() => void begin(channelId)}
            >
              <span className="feishu-qr-icon">▣</span>
              扫码登录微信
            </button>
          </div>
        </div>
      )}

      {channelId && phase === "expired" && (
        <div className="feishu-qr-tip">
          {STATUS_HINT.EXPIRED}
          <div style={{ marginTop: 8 }}>
            <button
              type="button"
              className="feishu-qr-trigger"
              onClick={() => void begin(channelId)}
            >
              <span className="feishu-qr-icon">▣</span>
              重新扫码
            </button>
          </div>
        </div>
      )}

      {channelId && phase === "error" && (
        <div className="feishu-qr-err">
          × {error || STATUS_HINT.ERROR}
          <div style={{ marginTop: 8 }}>
            <button
              type="button"
              className="link-button"
              onClick={() => void begin(channelId)}
            >
              重试
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
