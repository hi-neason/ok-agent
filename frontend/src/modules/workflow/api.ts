import type {
  AgentWorkflowBinding,
  AgentWorkflowBindingDraft,
  WorkflowCatalogItem,
  WorkflowSource,
  WorkflowSourceDraft,
} from "./types";

async function jsonOrThrow<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let detail = "";
    try {
      const data = await res.json();
      detail = data.message || data.detail || data.error || "";
    } catch {
      detail = await res.text().catch(() => "");
    }
    throw new Error(detail || `HTTP ${res.status}`);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export async function listSources(): Promise<WorkflowSource[]> {
  const res = await fetch("/api/v1/workflow/sources");
  return jsonOrThrow<WorkflowSource[]>(res);
}

export async function createSource(draft: WorkflowSourceDraft): Promise<WorkflowSource> {
  const res = await fetch("/api/v1/workflow/sources", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(draft),
  });
  return jsonOrThrow<WorkflowSource>(res);
}

export async function updateSource(
  id: string,
  draft: WorkflowSourceDraft,
): Promise<WorkflowSource> {
  const res = await fetch(`/api/v1/workflow/sources/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(draft),
  });
  return jsonOrThrow<WorkflowSource>(res);
}

export async function deleteSource(id: string): Promise<void> {
  const res = await fetch(`/api/v1/workflow/sources/${id}`, { method: "DELETE" });
  return jsonOrThrow<void>(res);
}

export async function setSourceEnabled(id: string, value: boolean): Promise<WorkflowSource> {
  const res = await fetch(
    `/api/v1/workflow/sources/${id}/enabled?value=${value}`,
    { method: "PATCH" },
  );
  return jsonOrThrow<WorkflowSource>(res);
}

export async function testSource(id: string): Promise<WorkflowSource> {
  const res = await fetch(`/api/v1/workflow/sources/${id}/test`, { method: "POST" });
  return jsonOrThrow<WorkflowSource>(res);
}

export async function syncSource(id: string): Promise<WorkflowCatalogItem[]> {
  const res = await fetch(`/api/v1/workflow/sources/${id}/sync`, { method: "POST" });
  return jsonOrThrow<WorkflowCatalogItem[]>(res);
}

export async function listCatalog(sourceId: string): Promise<WorkflowCatalogItem[]> {
  const res = await fetch(`/api/v1/workflow/sources/${sourceId}/catalog`);
  return jsonOrThrow<WorkflowCatalogItem[]>(res);
}

export async function updateCatalogDescription(
  itemId: string,
  description: string,
): Promise<WorkflowCatalogItem> {
  const res = await fetch(`/api/v1/workflow/sources/catalog/${itemId}/description`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ description }),
  });
  return jsonOrThrow<WorkflowCatalogItem>(res);
}

export async function listAgentBindings(
  agentId: string,
): Promise<AgentWorkflowBinding[]> {
  const res = await fetch(`/api/v1/agents/${agentId}/workflows`);
  return jsonOrThrow<AgentWorkflowBinding[]>(res);
}

export async function replaceAgentBindings(
  agentId: string,
  bindings: AgentWorkflowBindingDraft[],
): Promise<AgentWorkflowBinding[]> {
  const res = await fetch(`/api/v1/agents/${agentId}/workflows`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(bindings),
  });
  return jsonOrThrow<AgentWorkflowBinding[]>(res);
}
