import type { McpDraft, McpServer, McpTool } from "./types";
import type { Page } from "../shared";

export type McpInspection = {
  success: boolean;
  tools: McpTool[];
  message?: string;
};

export async function fetchServers(
  page = 0,
  size = 20,
): Promise<Page<McpServer>> {
  const response = await fetch(
    `/api/v1/mcp-servers?page=${page}&size=${size}`,
  );
  if (!response.ok) throw new Error("load failed");
  return (await response.json()) as Page<McpServer>;
}

export async function saveServer(
  payload: Record<string, unknown>,
  isNew: boolean,
  id?: string,
): Promise<McpServer> {
  const response = await fetch(
    isNew ? "/api/v1/mcp-servers" : `/api/v1/mcp-servers/${id}`,
    {
      method: isNew ? "POST" : "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
  );
  if (!response.ok) throw new Error("save failed");
  return (await response.json()) as McpServer;
}

export async function inspectServerById(id: string): Promise<McpInspection> {
  const response = await fetch(`/api/v1/mcp-servers/${id}/inspect`, {
    method: "POST",
  });
  const result = (await response.json().catch(() => null)) as McpInspection | null;
  if (!response.ok || !result?.success) {
    throw new Error(result?.message || "inspect failed");
  }
  return result;
}

export async function inspectServerByDraft(
  payload: Record<string, unknown>,
): Promise<McpInspection> {
  const response = await fetch("/api/v1/mcp-servers/inspect", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const result = (await response.json().catch(() => null)) as McpInspection | null;
  if (!response.ok || !result?.success) {
    throw new Error(result?.message || "inspect failed");
  }
  return result;
}

export async function deleteServer(id: string): Promise<void> {
  await fetch(`/api/v1/mcp-servers/${id}`, { method: "DELETE" });
}

export async function setServerEnabled(
  id: string,
  value: boolean,
): Promise<void> {
  const response = await fetch(
    `/api/v1/mcp-servers/${id}/enabled?value=${value}`,
    { method: "PATCH" },
  );
  if (!response.ok) throw new Error("toggle failed");
}

export async function fetchTools(id: string): Promise<McpTool[]> {
  const response = await fetch(`/api/v1/mcp-servers/${id}/tools`);
  if (!response.ok) throw new Error("tools failed");
  return (await response.json()) as McpTool[];
}

export type McpToolCallResult = {
  success: boolean;
  resultJson: string;
  message?: string;
  durationMs?: number;
};

export async function callTool(
  id: string,
  name: string,
  args: unknown,
): Promise<McpToolCallResult> {
  const response = await fetch(
    `/api/v1/mcp-servers/${id}/tools/${encodeURIComponent(name)}/call`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ arguments: args }),
    },
  );
  const result = (await response.json().catch(() => null)) as McpToolCallResult | null;
  if (!response.ok || !result) throw new Error("call failed");
  return result;
}
