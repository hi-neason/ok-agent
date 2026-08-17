import type { Persona, UpsertPersona } from "./types";

const BASE = "/api/v1";

/** Lists every per-agent persona stored for a user. */
export async function listPersonas(userId: string): Promise<Persona[]> {
  const response = await fetch(
    `${BASE}/persona/users/${encodeURIComponent(userId)}`,
  );
  if (!response.ok) throw new Error("list personas failed");
  return (await response.json()) as Persona[];
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
