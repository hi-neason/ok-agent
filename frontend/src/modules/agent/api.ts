import type {
  AgentForm,
  AgentItem,
  ChatMessage,
  Option,
  ValidationResponse,
} from "./types";

async function jsonOrThrow(res: Response): Promise<unknown> {
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(text || `HTTP ${res.status}`);
  }
  return res.status === 204 ? undefined : res.json();
}

export async function loadAgent(agentId: string): Promise<AgentItem> {
  const res = await fetch(`/api/v1/agents/${agentId}`);
  if (!res.ok) throw new Error("agent not found");
  return (await res.json()) as AgentItem;
}

export async function loadModels(): Promise<Option[]> {
  const res = await fetch("/api/v1/models");
  if (!res.ok) return [];
  const list = (await res.json()) as Array<Record<string, unknown>>;
  return list
    .filter((m) => m.enabled !== false)
    .map((m) => ({
      id: String(m.id),
      name: String(m.name),
      sub: `${m.provider} / ${m.modelId}`,
    }));
}

export async function loadMcpServers(): Promise<Option[]> {
  const res = await fetch("/api/v1/mcp-servers");
  if (!res.ok) return [];
  const list = (await res.json()) as Array<Record<string, unknown>>;
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
  const res = await fetch("/api/v1/skills");
  if (!res.ok) return [];
  const list = (await res.json()) as Array<Record<string, unknown>>;
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
  const res = await fetch("/api/v1/users");
  if (!res.ok) return [];
  const list = (await res.json()) as Array<Record<string, unknown>>;
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
    throw new Error(data?.detail || data?.message || "chat failed");
  }
  return { reply: data.reply ?? "", sessionId: data.sessionId };
}

export async function deleteSession(sessionId: string): Promise<void> {
  await fetch(`/api/v1/agents/sessions/${sessionId}`, { method: "DELETE" }).catch(
    () => undefined,
  );
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
  };
}

export type { ChatMessage };
