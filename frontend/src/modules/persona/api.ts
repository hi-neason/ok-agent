import type { Persona, UpsertPersona } from "./types";

const BASE = "/api/v1";

export async function fetchPersona(userId: string): Promise<Persona> {
  const response = await fetch(`${BASE}/persona/${encodeURIComponent(userId)}`);
  if (!response.ok) throw new Error("fetch persona failed");
  return (await response.json()) as Persona;
}

export async function savePersona(
  userId: string,
  payload: UpsertPersona,
): Promise<Persona> {
  const response = await fetch(`${BASE}/persona/${encodeURIComponent(userId)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!response.ok) throw new Error("save persona failed");
  return (await response.json()) as Persona;
}

export async function appendPersonaMemory(
  userId: string,
  delta: string,
): Promise<{ memory: string }> {
  const response = await fetch(
    `${BASE}/persona/${encodeURIComponent(userId)}/memory`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ delta }),
    },
  );
  if (!response.ok) throw new Error("append memory failed");
  return (await response.json()) as { memory: string };
}
