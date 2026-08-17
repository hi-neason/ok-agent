import type { DialogueTurn, SessionPage, SessionQuery, TraceSpan } from "./types";

/**
 * Read-only client for the runtime observability surface. It talks to `/api/v1/observe`,
 * which serves dialogue history produced by any runtime (debug preview today, real
 * runtime instances later), so this module never needs to know who wrote the data.
 */
function buildQuery(query: SessionQuery): string {
  const params = new URLSearchParams();
  const append = (key: string, value: string | number | undefined) => {
    if (value === undefined) return;
    const text = String(value).trim();
    if (text) params.set(key, text);
  };
  append("sessionId", query.sessionId);
  append("userId", query.userId);
  append("agentId", query.agentId);
  append("from", query.from);
  append("to", query.to);
  append("page", query.page ?? 0);
  append("size", query.size ?? 20);
  return params.toString();
}

export async function searchSessions(query: SessionQuery): Promise<SessionPage> {
  const response = await fetch(`/api/v1/observe/sessions?${buildQuery(query)}`);
  if (!response.ok) throw new Error("sessions failed");
  return (await response.json()) as SessionPage;
}

export async function fetchTurns(sessionId: string): Promise<DialogueTurn[]> {
  const response = await fetch(
    `/api/v1/observe/sessions/${encodeURIComponent(sessionId)}/turns`,
  );
  if (!response.ok) throw new Error("turns failed");
  return (await response.json()) as DialogueTurn[];
}

export async function fetchTrace(traceId: string): Promise<TraceSpan[]> {
  const response = await fetch(
    `/api/v1/observe/traces/${encodeURIComponent(traceId)}`,
  );
  if (response.status === 404) return [];
  if (!response.ok) throw new Error("trace failed");
  return (await response.json()) as TraceSpan[];
}
