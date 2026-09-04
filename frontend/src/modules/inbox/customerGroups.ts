import type { ConversationWorkItem } from "./types";

export function customerKey(item: ConversationWorkItem): string {
  return item.userId ? `user:${item.userId}` : `anonymous:${item.sessionId}`;
}

export function channelKey(item: ConversationWorkItem): string {
  return item.channelType || (item.sessionId.startsWith("dbg-") ? "DEBUG"
    : item.sessionId.startsWith("web:") ? "WEB" : "UNKNOWN");
}

export function groupCustomers(items: ConversationWorkItem[]) {
  const groups = new Map<string, ConversationWorkItem[]>();
  for (const item of [...items].sort((a, b) =>
    b.updatedAt.localeCompare(a.updatedAt) || a.sessionId.localeCompare(b.sessionId))) {
    const key = customerKey(item);
    const sessions = groups.get(key) ?? [];
    sessions.push(item);
    groups.set(key, sessions);
  }
  return [...groups].map(([key, sessions]) => ({
    key, sessions, latest: sessions[0],
    channels: [...new Set(sessions.map(channelKey))],
  }));
}
