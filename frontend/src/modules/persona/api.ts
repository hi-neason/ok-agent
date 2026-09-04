import type { Persona, UpsertPersona } from "./types";
import { listAgents } from "../agent/api";
import { loadAllPages } from "../shared/loadAllPages";

export function fetchPersonaAgents() {
  return loadAllPages(listAgents);
}

const BASE = "/api/v1";

/** Coverage map: userId -> agentIds that hold a persona. */
export async function fetchPersonaCoverage(): Promise<Record<string, string[]>> {
  const response = await fetch(`${BASE}/persona/coverage`);
  if (!response.ok) throw new Error("fetch coverage failed");
  return (await response.json()) as Record<string, string[]>;
}

/** Lists every per-agent persona stored for a user. */
export async function listPersonas(userId: string): Promise<Persona[]> {
  const response = await fetch(
    `${BASE}/persona/users/${encodeURIComponent(userId)}`,
  );
  if (!response.ok) throw new Error("list personas failed");
  return (await response.json()) as Persona[];
}

/** WYSIWYG preview of the persona block actually injected for an agent. */
export async function fetchInjectionPreview(
  userId: string,
  agentId: string,
): Promise<{ mode: string; block: string }> {
  const response = await fetch(
    `${BASE}/persona/users/${encodeURIComponent(userId)}/agents/${encodeURIComponent(agentId)}/injection-preview`,
  );
  if (!response.ok) throw new Error("fetch injection preview failed");
  return (await response.json()) as { mode: string; block: string };
}

/** Fetches the persona a specific agent holds for a user. */
export async function fetchPersona(
  userId: string,
  agentId: string,
): Promise<Persona> {
  const response = await fetch(
    `${BASE}/persona/users/${encodeURIComponent(userId)}/agents/${encodeURIComponent(agentId)}`,
  );
  if (!response.ok) throw new Error("fetch persona failed");
  return (await response.json()) as Persona;
}

export async function savePersona(
  userId: string,
  agentId: string,
  payload: UpsertPersona,
): Promise<Persona> {
  const response = await fetch(
    `${BASE}/persona/users/${encodeURIComponent(userId)}/agents/${encodeURIComponent(agentId)}`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
  );
  if (!response.ok) throw new Error("save persona failed");
  return (await response.json()) as Persona;
}

export async function appendPersonaMemory(
  userId: string,
  agentId: string,
  delta: string,
): Promise<{ memory: string }> {
  const response = await fetch(
    `${BASE}/persona/users/${encodeURIComponent(userId)}/agents/${encodeURIComponent(agentId)}/memory`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ delta }),
    },
  );
  if (!response.ok) throw new Error("append memory failed");
  return (await response.json()) as { memory: string };
}
