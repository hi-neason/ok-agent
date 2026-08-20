import type { ChannelIdentity } from "./types";

export const channelTypeLabel: Record<string, string> = {
  FEISHU: "飞书",
  DINGTALK: "钉钉",
  WECOM: "企业微信",
  WECHAT: "微信",
};

export function channelLabel(type: string): string {
  return channelTypeLabel[type] ?? type;
}

function maskId(id: string): string {
  if (!id) return "—";
  if (id.length <= 8) return id;
  return `${id.slice(0, 4)}…${id.slice(-4)}`;
}

/**
 * Renders a provider identity in a human-friendly way. The stored display_name is often just the
 * raw provider id (e.g. a Feishu open_id like "ou_6de..."), which reads as "wrong data" in the UI.
 * When that's the case we show a channel-kind label plus a masked external id instead.
 */
export function channelFriendlyName(c: ChannelIdentity): { name: string; sub: string } {
  const label = channelLabel(c.channelType);
  const dn = c.displayName ?? "";
  const looksLikeId = !dn || dn === c.externalId || /^ou_/i.test(dn) || /^[a-z0-9]{16,}$/i.test(dn);
  if (looksLikeId) {
    return { name: `${label}用户`, sub: maskId(c.externalId) };
  }
  return { name: dn, sub: maskId(c.externalId) };
}

export function formatInstant(value?: string | null): string {
  if (!value) return "—";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleString("zh-CN", { hour12: false });
}
