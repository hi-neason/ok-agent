import type { ChannelIdentity, UserDetail, UserGroupItem, UserItem } from "./types";
import type { Page } from "../shared";

const BASE = "/api/v1";

/** Full list of groups — used for the user-edit dropdown. */
export async function fetchUserGroups(): Promise<UserGroupItem[]> {
  const response = await fetch(`${BASE}/user-groups?page=0&size=1000`);
  if (!response.ok) return [];
  const data = (await response.json()) as Page<UserGroupItem>;
  return data.content ?? [];
}

/** Paged groups for the group-management tab. */
export async function fetchUserGroupsPage(page = 0, size = 20): Promise<Page<UserGroupItem>> {
  const response = await fetch(`${BASE}/user-groups?page=${page}&size=${size}`);
  if (!response.ok) return { content: [], totalElements: 0, totalPages: 0, number: page, size } as Page<UserGroupItem>;
  return (await response.json()) as Page<UserGroupItem>;
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

/** Full list of users — used for the merge dropdown. */
export async function fetchUsers(): Promise<UserItem[]> {
  const response = await fetch(`${BASE}/users?page=0&size=1000`);
  if (!response.ok) return [];
  const data = (await response.json()) as Page<UserItem>;
  return data.content ?? [];
}

/** Paged users for the user-management tab. */
export async function fetchUsersPage(page = 0, size = 20): Promise<Page<UserItem>> {
  const response = await fetch(`${BASE}/users?page=${page}&size=${size}`);
  if (!response.ok) return { content: [], totalElements: 0, totalPages: 0, number: page, size } as Page<UserItem>;
  return (await response.json()) as Page<UserItem>;
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

export async function fetchUserDetail(id: string): Promise<UserDetail> {
  const response = await fetch(`${BASE}/users/${id}/detail`);
  if (!response.ok) throw new Error("fetch user detail failed");
  return (await response.json()) as UserDetail;
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
