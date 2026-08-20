import type { ChannelInput, ChannelItem } from "./types";

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
  throw new Error(detail || `请求失败（HTTP ${response.status}）`);
}

export async function fetchChannels(): Promise<ChannelItem[]> {
  const response = await fetch(BASE);
  return parse<ChannelItem[]>(response);
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
    throw new Error(`删除失败（HTTP ${response.status}）`);
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
