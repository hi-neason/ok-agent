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

export async function listAgents(): Promise<AgentOption[]> {
  const res = await fetch("/api/v1/agents?page=0&size=1000");
  if (!res.ok) return [];
  const data = (await res.json()) as Page<{
    id: string;
    agentKey: string;
    name: string;
    enabled: boolean;
  }>;
  return data.content
    .filter((a) => a.enabled !== false)
    .map((a) => ({
      id: String(a.id),
      agentKey: String(a.agentKey ?? ""),
      name: String(a.name ?? ""),
    }));
}

export async function listChannels(): Promise<ChannelOption[]> {
  const res = await fetch("/api/v1/channels?page=0&size=1000");
  if (!res.ok) return [];
  const data = (await res.json()) as Page<ChannelOption>;
  return data.content;
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
