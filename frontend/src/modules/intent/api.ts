import type {
  CreateIntentRequest,
  IntentDto,
  IntentNode,
  UpdateIntentRequest,
} from "./types";

async function jsonOrThrow(res: Response): Promise<unknown> {
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(text || `HTTP ${res.status}`);
  }
  return res.status === 204 ? undefined : res.json();
}

export async function loadIntentTree(): Promise<IntentNode[]> {
  const res = await fetch("/api/v1/intents/tree");
  if (!res.ok) throw new Error("加载意图树失败");
  return (await res.json()) as IntentNode[];
}

export async function createIntent(req: CreateIntentRequest): Promise<IntentDto> {
  const res = await fetch("/api/v1/intents", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  return (await jsonOrThrow(res)) as IntentDto;
}

export async function updateIntent(id: string, req: UpdateIntentRequest): Promise<IntentDto> {
  const res = await fetch(`/api/v1/intents/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  return (await jsonOrThrow(res)) as IntentDto;
}

export async function deleteIntent(id: string): Promise<void> {
  await fetch(`/api/v1/intents/${id}`, { method: "DELETE" }).catch(() => undefined);
}

export function flatten(nodes: IntentNode[]): IntentDto[] {
  const out: IntentDto[] = [];
  const walk = (ns: IntentNode[]) => {
    for (const n of ns) {
      out.push(n.node);
      walk(n.children);
    }
  };
  walk(nodes);
  return out;
}
