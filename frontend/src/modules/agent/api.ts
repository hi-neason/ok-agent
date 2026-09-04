import type { Page } from "../shared";
import type {
  AgentForm,
  AgentItem,
  AgentSubagentConfig,
  ChatMessage,
  Option,
  ValidationResponse,
} from "./types";

async function jsonOrThrow(res: Response): Promise<unknown> {
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as
      | { message?: unknown; detail?: unknown }
      | null;
    const message = body?.message ?? body?.detail;
    throw new Error(typeof message === "string" ? message : `HTTP ${res.status}`);
  }
  return res.status === 204 ? undefined : res.json();
}

async function loadOptions(path: string): Promise<Array<Record<string, unknown>>> {
  const items: Array<Record<string, unknown>> = [];
  for (let page = 0; ; page += 1) {
    const res = await fetch(`${path}?page=${page}&size=100`);
    const data = await jsonOrThrow(res);
    if (!data || typeof data !== "object" || !("content" in data) || !("totalPages" in data)) {
      throw new Error("Invalid option page response");
    }
    const { content, totalPages } = data;
    if (!Array.isArray(content) || typeof totalPages !== "number" || !Number.isInteger(totalPages) || totalPages < 0) {
      throw new Error("Invalid option page response");
    }
    for (const item of content) {
      if (!item || typeof item !== "object" || Array.isArray(item)) {
        throw new Error("Invalid option item response");
      }
      items.push(item as Record<string, unknown>);
    }
    if (page + 1 >= totalPages) return items;
    if (content.length === 0) throw new Error("Incomplete option page response");
  }
}

export async function loadAgent(agentId: string): Promise<AgentItem> {
  const res = await fetch(`/api/v1/agents/${agentId}`);
  if (!res.ok) throw new Error("agent not found");
  return (await res.json()) as AgentItem;
}

export async function loadModels(): Promise<Option[]> {
  const list = await loadOptions("/api/v1/models");
  return list
    .filter((m) => m.enabled !== false)
    .map((m) => ({
      id: String(m.id),
      name: String(m.name),
      sub: `${m.provider} / ${m.modelId}`,
    }));
}

export type AgentOption = {
  id: string;
  agentKey: string;
  name: string;
  description: string;
};

export async function listAgents(
  page = 0,
  size = 20,
): Promise<Page<AgentItem>> {
  const res = await fetch(`/api/v1/agents?page=${page}&size=${size}`);
  if (!res.ok) throw new Error("agents failed");
  return (await res.json()) as Page<AgentItem>;
}

export async function loadAgents(): Promise<AgentOption[]> {
  const list = await loadOptions("/api/v1/agents");
  return list
    .filter((a) => a.enabled !== false)
    .map((a) => ({
      id: String(a.id),
      agentKey: String(a.agentKey ?? ""),
      name: String(a.name ?? ""),
      description: String(a.description ?? ""),
    }));
}

export async function loadMcpServers(): Promise<Option[]> {
  const list = await loadOptions("/api/v1/mcp-servers");
  return list
    .filter((m) => m.enabled !== false)
    .map((m) => ({
      id: String(m.id),
      name: String(m.name),
      sub: String(m.serverKey),
    }));
}

export async function loadMcpTools(
  servers: Option[],
): Promise<Record<string, string[]>> {
  const entries = await Promise.all(
    servers.map(async (m) => {
      const res = await fetch(`/api/v1/mcp-servers/${m.id}/tools`);
      const tools = res.ok
        ? ((await res.json()) as Array<Record<string, unknown>>)
        : [];
      return [m.id, tools.map((t) => String(t.name))] as const;
    }),
  );
  return Object.fromEntries(entries);
}

export async function loadSkills(): Promise<Option[]> {
  const list = await loadOptions("/api/v1/skills");
  return list
    .filter((s) => s.enabled !== false)
    .map((s) => ({
      id: String(s.id),
      name: String(s.name),
      sub: String(s.skillKey),
    }));
}

export type DebugUser = { userId: string; username: string; displayName: string };

export async function loadUsers(): Promise<DebugUser[]> {
  const list = await loadOptions("/api/v1/users");
  return list
    .filter((u) => u.enabled !== false)
    .map((u) => ({
      userId: String(u.userId),
      username: String(u.username),
      displayName: String(u.displayName),
    }));
}

export async function saveAgentConfig(
  agentId: string,
  payload: AgentForm,
): Promise<AgentItem> {
  const res = await fetch(`/api/v1/agents/${agentId}/configuration`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(toConfigPayload(agentId, payload)),
  });
  if (!res.ok) throw new Error("save failed");
  return (await res.json()) as AgentItem;
}

export async function sendChat(
  agentId: string,
  message: string,
  sessionId: string | null,
  userId: string,
): Promise<{ reply: string; sessionId?: string }> {
  const res = await fetch(`/api/v1/agents/${agentId}/chat`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message, sessionId, userId }),
  });
  const data = (await res.json().catch(() => null)) as
    | { reply?: string; sessionId?: string; detail?: string; message?: string }
    | null;
  if (!res.ok || !data) {
    throw new Error(data?.message || data?.detail || "chat failed");
  }
  return { reply: data.reply ?? "", sessionId: data.sessionId };
}

export async function deleteSession(sessionId: string, userId: string): Promise<void> {
  const response = await fetch(
    `/api/v1/agents/sessions/${sessionId}?userId=${encodeURIComponent(userId)}`,
    { method: "DELETE" },
  );
  await jsonOrThrow(response);
}

export async function validateAgentConfig(
  agentId: string,
  payload: AgentForm,
): Promise<ValidationResponse> {
  const res = await fetch(`/api/v1/agents/${agentId}/configuration/validate`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(toConfigPayload(agentId, payload)),
  });
  return (await jsonOrThrow(res)) as ValidationResponse;
}

export function toConfigPayload(_agentId: string, form: AgentForm) {
  return {
    systemPrompt: form.systemPrompt,
    welcomeMessage: form.welcomeMessage,
    modelAssetId: form.modelAssetId || null,
    temperature: form.temperature,
    topP: form.topP,
    topK: form.topK,
    maxTokens: form.maxTokens,
    maxIters: form.maxIters,
    modelTimeoutSeconds: form.modelTimeoutSeconds,
    toolTimeoutSeconds: form.toolTimeoutSeconds,
    maxRetries: form.maxRetries,
    permissionMode: form.permissionMode,
    parallelToolCalls: form.parallelToolCalls,
    compactionEnabled: form.compactionEnabled,
    maxContextTokens: form.maxContextTokens,
    toolResultEvictionEnabled: form.toolResultEvictionEnabled,
    tracingEnabled: form.tracingEnabled,
    mcpServerIds: [...form.boundMcp],
    skillIds: [...form.boundSkills],
    mcpToolFilters: form.mcpToolFilters,
    memoryEnabled: form.memoryEnabled,
    memoryFlushMode: form.memoryFlushMode,
    memoryFlushIntervalMinutes: form.memoryFlushIntervalMinutes,
    memoryConsolidationIntervalMinutes: form.memoryConsolidationIntervalMinutes,
    memoryDailyRetentionDays: form.memoryDailyRetentionDays,
    memorySessionRetentionDays: form.memorySessionRetentionDays,
    personaExtractEnabled: form.personaExtractEnabled,
    personaInjectionMode: form.personaInjectionMode,
    personaPromptTemplate: form.personaPromptTemplate,
    workspaceMode: form.workspaceMode,
    workspaceIsolationScope: form.workspaceIsolationScope,
    workspaceContextEnabled: form.workspaceContextEnabled,
    shellEnabled: form.shellEnabled,
    dockerImage: form.dockerImage,
    sandboxMemoryMb: form.sandboxMemoryMb,
    sandboxCpuCount: form.sandboxCpuCount,
    subagentsJson: JSON.stringify(form.subagents ?? []),
  };
}

export function parseSubagents(raw: string | undefined): AgentSubagentConfig[] {
  if (!raw) return [];
  try {
    const arr = JSON.parse(raw);
    if (!Array.isArray(arr)) return [];
    return arr.map((x: Record<string, unknown>) => ({
      agentId: x.agentId ? String(x.agentId) : null,
      intentKeys: Array.isArray(x.intentKeys) ? (x.intentKeys as unknown[]).map(String) : [],
    }));
  } catch {
    return [];
  }
}

export type { ChatMessage };
