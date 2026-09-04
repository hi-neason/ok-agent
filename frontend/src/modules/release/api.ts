import type { Page } from "../shared";
import type {
  AgentOption,
  ChannelOption,
  ReleaseItem,
  VersionDetail,
  VersionSummary,
} from "./types";

async function parse<T>(response: Response): Promise<T> {
  if (response.ok) {
    if (response.status === 204) return undefined as T;
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

async function listOptions<T>(path: string): Promise<T[]> {
  const items: T[] = [];
  for (let page = 0; ; page += 1) {
    const data = await parse<Page<T>>(await fetch(`${path}?page=${page}&size=100`));
    if (!data || !Array.isArray(data.content) || !Number.isInteger(data.totalPages) || data.totalPages < 0) {
      throw new Error(i18n.t("common.requestFailed", { status: "Invalid pagination" }));
    }
    items.push(...data.content);
    if (page + 1 >= data.totalPages) return items;
    if (data.content.length === 0) {
      throw new Error(i18n.t("common.requestFailed", { status: "Incomplete pagination" }));
    }
  }
}

export async function listAgents(): Promise<AgentOption[]> {
  const items = await listOptions<{
    id: string;
    agentKey: string;
    name: string;
    enabled: boolean;
  }>("/api/v1/agents");
  return items
    .filter((a) => a.enabled !== false)
    .map((a) => ({
      id: String(a.id),
      agentKey: String(a.agentKey ?? ""),
      name: String(a.name ?? ""),
    }));
}

export async function listChannels(): Promise<ChannelOption[]> {
  return listOptions<ChannelOption>("/api/v1/channels");
}

export async function listVersions(
  agentId: string,
): Promise<VersionSummary[]> {
  const res = await fetch(`/api/v1/agents/${agentId}/versions`);
  return parse<VersionSummary[]>(res);
}

export async function getVersion(
  agentId: string,
  versionId: string,
): Promise<VersionDetail> {
  const res = await fetch(`/api/v1/agents/${agentId}/versions/${versionId}`);
  return parse<VersionDetail>(res);
}

export async function createVersion(
  agentId: string,
  label: string,
  changelog: string,
): Promise<VersionDetail> {
  const res = await fetch(`/api/v1/agents/${agentId}/versions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ label, changelog }),
  });
  return parse<VersionDetail>(res);
}

export async function listAgentReleases(
  agentId: string,
): Promise<ReleaseItem[]> {
  const res = await fetch(`/api/v1/agents/${agentId}/releases`);
  return parse<ReleaseItem[]>(res);
}

export async function publishRelease(
  agentId: string,
  versionNo: number,
  channelId: string,
): Promise<ReleaseItem> {
  const res = await fetch(`/api/v1/agents/${agentId}/releases`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ versionNo, channelId }),
  });
  return parse<ReleaseItem>(res);
}

export async function getCurrentRelease(
  channelId: string,
): Promise<ReleaseItem | null> {
  const res = await fetch(`/api/v1/channels/${channelId}/current-release`);
  if (res.status === 204) return null;
  return parse<ReleaseItem>(res);
}

export async function listChannelReleases(
  channelId: string,
): Promise<ReleaseItem[]> {
  const res = await fetch(`/api/v1/channels/${channelId}/releases`);
  return parse<ReleaseItem[]>(res);
}

export async function rollbackChannel(
  channelId: string,
): Promise<ReleaseItem> {
  const res = await fetch(`/api/v1/channels/${channelId}/rollback`, {
    method: "POST",
  });
  return parse<ReleaseItem>(res);
}
import i18n from "../../i18n";
