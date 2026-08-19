import type { ChannelIdentity, UserGroupItem, UserItem } from "./types";

const BASE = "/api/v1";

export async function fetchUserGroups(): Promise<UserGroupItem[]> {
  const response = await fetch(`${BASE}/user-groups`);
  if (!response.ok) return [];
  return (await response.json()) as UserGroupItem[];
}

export async function saveUserGroup(
  group: Partial<UserGroupItem> & { groupKey: string; name: string },
): Promise<UserGroupItem> {
  const existing = Boolean(group.id);
  const response = await fetch(
    existing ? `${BASE}/user-groups/${group.id}` : `${BASE}/user-groups`,
    {
      method: existing ? "PUT" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        groupKey: group.groupKey,
        name: group.name,
        description: group.description ?? "",
        enabled: group.enabled ?? true,
      }),
    },
  );
  if (!response.ok) throw new Error("save group failed");
  return (await response.json()) as UserGroupItem;
}

export async function deleteUserGroup(id: string): Promise<void> {
  const response = await fetch(`${BASE}/user-groups/${id}`, { method: "DELETE" });
  if (!response.ok) throw new Error("delete group failed");
}

export async function fetchUsers(groupId?: string): Promise<UserItem[]> {
  const url = groupId
    ? `${BASE}/users?groupId=${encodeURIComponent(groupId)}`
    : `${BASE}/users`;
  const response = await fetch(url);
  if (!response.ok) return [];
  return (await response.json()) as UserItem[];
}

export async function saveUser(
  user: Partial<UserItem> & { username: string; displayName: string },
): Promise<UserItem> {
  const existing = Boolean(user.id);
  const response = await fetch(
    existing ? `${BASE}/users/${user.id}` : `${BASE}/users`,
    {
      method: existing ? "PUT" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username: user.username,
        displayName: user.displayName,
        email: user.email ?? "",
        phone: user.phone ?? "",
        groupId: user.groupId ?? null,
        enabled: user.enabled ?? true,
      }),
    },
  );
  if (!response.ok) throw new Error("save user failed");
  return (await response.json()) as UserItem;
}

export async function deleteUser(id: string): Promise<void> {
  const response = await fetch(`${BASE}/users/${id}`, { method: "DELETE" });
  if (!response.ok) throw new Error("delete user failed");
}

export async function fetchUserChannels(id: string): Promise<ChannelIdentity[]> {
  const response = await fetch(`${BASE}/users/${id}/channels`);
  if (!response.ok) throw new Error("fetch user channels failed");
  return (await response.json()) as ChannelIdentity[];
}

export async function mergeUsers(
  primaryId: string,
  secondaryId: string,
): Promise<void> {
  const response = await fetch(`${BASE}/users/${primaryId}/merge`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ secondaryId }),
  });
  if (!response.ok) {
    const text = await response.text().catch(() => "");
    throw new Error(text || "merge failed");
  }
}
