import type {
  AgentKnowledgeBinding,
  AgentKnowledgeBindingDraft,
  KnowledgeCatalogItem,
  KnowledgeSource,
  KnowledgeSourceDraft,
} from "./types";
import type { Page } from "../shared";

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

export async function listSources(
  page = 0,
  size = 20,
): Promise<Page<KnowledgeSource>> {
  const res = await fetch(`/api/v1/knowledge/sources?page=${page}&size=${size}`);
  return jsonOrThrow<Page<KnowledgeSource>>(res);
}

export async function createSource(draft: KnowledgeSourceDraft): Promise<KnowledgeSource> {
  const res = await fetch("/api/v1/knowledge/sources", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(draft),
  });
  return jsonOrThrow<KnowledgeSource>(res);
}

export async function updateSource(
  id: string,
  draft: KnowledgeSourceDraft,
): Promise<KnowledgeSource> {
  const res = await fetch(`/api/v1/knowledge/sources/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(draft),
  });
  return jsonOrThrow<KnowledgeSource>(res);
}

export async function deleteSource(id: string): Promise<void> {
  const res = await fetch(`/api/v1/knowledge/sources/${id}`, { method: "DELETE" });
  return jsonOrThrow<void>(res);
}

export async function setSourceEnabled(id: string, value: boolean): Promise<KnowledgeSource> {
  const res = await fetch(
    `/api/v1/knowledge/sources/${id}/enabled?value=${value}`,
    { method: "PATCH" },
  );
  return jsonOrThrow<KnowledgeSource>(res);
}

export async function testSource(id: string): Promise<KnowledgeSource> {
  const res = await fetch(`/api/v1/knowledge/sources/${id}/test`, { method: "POST" });
  return jsonOrThrow<KnowledgeSource>(res);
}

export async function syncSource(id: string): Promise<KnowledgeCatalogItem[]> {
  const res = await fetch(`/api/v1/knowledge/sources/${id}/sync`, { method: "POST" });
  return jsonOrThrow<KnowledgeCatalogItem[]>(res);
}

export async function listCatalog(sourceId: string): Promise<KnowledgeCatalogItem[]> {
  const res = await fetch(`/api/v1/knowledge/sources/${sourceId}/catalog`);
  return jsonOrThrow<KnowledgeCatalogItem[]>(res);
}

export async function updateCatalogDescription(
  itemId: string,
  description: string,
): Promise<KnowledgeCatalogItem> {
  const res = await fetch(`/api/v1/knowledge/sources/catalog/${itemId}/description`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ description }),
  });
  return jsonOrThrow<KnowledgeCatalogItem>(res);
}

export async function listAgentBindings(
  agentId: string,
): Promise<AgentKnowledgeBinding[]> {
  const res = await fetch(`/api/v1/agents/${agentId}/knowledge`);
  return jsonOrThrow<AgentKnowledgeBinding[]>(res);
}

export async function replaceAgentBindings(
  agentId: string,
  bindings: AgentKnowledgeBindingDraft[],
): Promise<AgentKnowledgeBinding[]> {
  const res = await fetch(`/api/v1/agents/${agentId}/knowledge`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(bindings),
  });
  return jsonOrThrow<AgentKnowledgeBinding[]>(res);
}
