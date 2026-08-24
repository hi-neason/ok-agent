import type { ChannelIdentity } from "./types";
import type { TFunction } from "i18next";

export function channelLabel(type: string, t: TFunction): string {
  return ["FEISHU", "DINGTALK", "WECOM", "WECHAT"].includes(type)
    ? t(`channels.types.${type}`)
    : type;
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
export function channelFriendlyName(c: ChannelIdentity, t: TFunction): { name: string; sub: string } {
  const label = channelLabel(c.channelType, t);
  const dn = c.displayName ?? "";
  const looksLikeId = !dn || dn === c.externalId || /^ou_/i.test(dn) || /^[a-z0-9]{16,}$/i.test(dn);
  if (looksLikeId) {
    return { name: t("users.channelUser", { channel: label }), sub: maskId(c.externalId) };
  }
  return { name: dn, sub: maskId(c.externalId) };
}

export function formatInstant(value: string | null | undefined, locale: string): string {
  if (!value) return "—";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleString(locale, { hour12: false });
}
