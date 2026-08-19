import type { ChannelUser } from "./types";

const BASE = "/api/v1/channel-users";

export async function fetchChannelUsers(
  channelKey?: string,
): Promise<ChannelUser[]> {
  const params = new URLSearchParams({ limit: "500" });
  if (channelKey) params.set("channelKey", channelKey);
  const res = await fetch(`${BASE}?${params.toString()}`);
  if (!res.ok) throw new Error(`加载失败（HTTP ${res.status}）`);
  return (await res.json()) as ChannelUser[];
}

export function channelTypeLabel(type: string): string {
  switch (type) {
    case "FEISHU":
      return "飞书";
    case "DINGTALK":
      return "钉钉";
    case "WECOM":
      return "企业微信";
    case "WECHAT":
      return "微信";
    default:
      return type;
  }
}

export function formatTime(iso: string): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("zh-CN", { hour12: false });
}
