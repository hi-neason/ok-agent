import type { Page } from "../shared";
import type { ChannelInput, ChannelItem, WechatIlinkStatus } from "./types";

const BASE = "/api/v1/channels";

async function parse<T>(response: Response): Promise<T> {
  if (response.ok) {
    return (await response.json()) as T;
  }
  let detail = "";
  try {
    const body = await response.json();
    detail = body.detail || body.message || "";
  } catch {
    /* ignore */
  }
  throw new Error(detail || i18n.t("common.requestFailed", { status: response.status }));
}

export async function fetchChannels(
  page = 0,
  size = 20,
): Promise<Page<ChannelItem>> {
  const response = await fetch(`${BASE}?page=${page}&size=${size}`);
  return parse<Page<ChannelItem>>(response);
}

export async function saveChannel(
  id: string | null,
  input: ChannelInput,
): Promise<ChannelItem> {
  const response = await fetch(id ? `${BASE}/${id}` : BASE, {
    method: id ? "PUT" : "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return parse<ChannelItem>(response);
}

export async function setChannelEnabled(
  id: string,
  enabled: boolean,
): Promise<ChannelItem> {
  const response = await fetch(
    `${BASE}/${id}/enabled?value=${enabled ? "true" : "false"}`,
    { method: "PATCH" },
  );
  return parse<ChannelItem>(response);
}

export async function setChannelRuntime(
  id: string,
  action: "start" | "stop",
): Promise<ChannelItem> {
  const response = await fetch(`${BASE}/${id}/${action}`, { method: "POST" });
  return parse<ChannelItem>(response);
}

export async function deleteChannel(id: string): Promise<void> {
  const response = await fetch(`${BASE}/${id}`, { method: "DELETE" });
  if (!response.ok && response.status !== 204) {
    throw new Error(i18n.t("common.deleteFailed", { status: response.status }));
  }
}

export type FeishuRegisterStatus = {
  state: "STARTING" | "WAITING_SCAN" | "SUCCESS" | "FAILED" | "EXPIRED" | "NOT_FOUND";
  qrUrl: string | null;
  appId: string | null;
  appSecret: string | null;
  expireAt: number;
  error: string | null;
};

export async function startFeishuRegistration(): Promise<{ sessionId: string }> {
  const response = await fetch(`${BASE}/feishu/register/start`, { method: "POST" });
  return parse<{ sessionId: string }>(response);
}

export async function pollFeishuRegistration(sessionId: string): Promise<FeishuRegisterStatus> {
  const response = await fetch(`${BASE}/feishu/register/${sessionId}`);
  return parse<FeishuRegisterStatus>(response);
}

// ---------- WeChat iLink (ClawBot) independent QR registration (create flow) ----------

export type WechatRegisterStatus = {
  state: "STARTING" | "WAITING_SCAN" | "SCANNED" | "SUCCESS" | "FAILED" | "EXPIRED" | "NOT_FOUND";
  /** The `qrcode_img_content` payload to render as a QR image. */
  qrcodePayload: string | null;
  botId: string | null;
  ilinkUserId: string | null;
  error: string | null;
  expireAt: number;
  loginId: string | null;
};

export async function startWechatRegistration(apiBase?: string, channelVersion?: string): Promise<{ loginId: string }> {
  const response = await fetch(`${BASE}/wechat/register/start`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ apiBase: apiBase ?? null, channelVersion: channelVersion ?? null }),
  });
  return parse<{ loginId: string }>(response);
}

export async function pollWechatRegistration(loginId: string): Promise<WechatRegisterStatus> {
  const response = await fetch(`${BASE}/wechat/register/${loginId}`);
  return parse<WechatRegisterStatus>(response);
}

// ---------- DingTalk scan-QR to create/bind a robot (create flow) ----------

export type DingTalkRegisterStatus = {
  state: "STARTING" | "WAITING_SCAN" | "SUCCESS" | "FAILED" | "EXPIRED" | "NOT_FOUND";
  /** The verification URL rendered as a QR code (opened in DingTalk to authorize the robot). */
  verificationUrl: string | null;
  appKey: string | null;
  error: string | null;
  expireAt: number;
  intervalSeconds: number;
  loginId: string | null;
};

export type DingTalkStartedSession = {
  loginId: string;
  verificationUrl: string;
  userCode: string | null;
  expireAt: number;
  intervalSeconds: number;
};

export async function startDingTalkRegistration(): Promise<DingTalkStartedSession> {
  const response = await fetch(`${BASE}/dingtalk/register/start`, { method: "POST" });
  return parse<DingTalkStartedSession>(response);
}

export async function pollDingTalkRegistration(loginId: string): Promise<DingTalkRegisterStatus> {
  const response = await fetch(`${BASE}/dingtalk/register/${loginId}`);
  return parse<DingTalkRegisterStatus>(response);
}

// ---------- WeChat iLink (ClawBot) per-channel QR login (edit flow) ----------

export async function startWechatLogin(id: string): Promise<WechatIlinkStatus> {
  const response = await fetch(`${BASE}/${id}/wechat/login/start`, { method: "POST" });
  return parse<WechatIlinkStatus>(response);
}

export async function pollWechatLogin(id: string): Promise<WechatIlinkStatus> {
  const response = await fetch(`${BASE}/${id}/wechat/login/poll`, { method: "POST" });
  return parse<WechatIlinkStatus>(response);
}

export async function fetchWechatLogin(id: string): Promise<WechatIlinkStatus> {
  const response = await fetch(`${BASE}/${id}/wechat/login`);
  return parse<WechatIlinkStatus>(response);
}

export async function wechatLogout(id: string): Promise<WechatIlinkStatus> {
  const response = await fetch(`${BASE}/${id}/wechat/logout`, { method: "POST" });
  return parse<WechatIlinkStatus>(response);
}
import i18n from "../../i18n";
