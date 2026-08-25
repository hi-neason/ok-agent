import type { DialogueTurn } from "../observe/types";
import type {
  ConversationWorkItem,
  ConversationWorkItemPage,
  ConversationOutcome,
  ConversationOutcomeDraft,
  CustomerCase,
  CustomerCaseType,
  InboxOperator,
  WorkPriority,
  WorkStatus,
} from "./types";

async function jsonOrThrow<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.json().catch(() => null) as
      | { detail?: string; message?: string }
      | null;
    throw new Error(body?.detail || body?.message || `HTTP ${response.status}`);
  }
  return (await response.json()) as T;
}

export async function listWorkItems(
  status?: WorkStatus,
): Promise<ConversationWorkItemPage> {
  const params = new URLSearchParams({ page: "0", size: "100" });
  if (status) params.set("status", status);
  return jsonOrThrow(await fetch(`/api/v1/inbox/sessions?${params}`));
}

export async function listOperators(): Promise<InboxOperator[]> {
  return jsonOrThrow(await fetch("/api/v1/inbox/sessions/operators"));
}

export async function getTurns(sessionId: string): Promise<DialogueTurn[]> {
  return jsonOrThrow(
    await fetch(`/api/v1/observe/sessions/${encodeURIComponent(sessionId)}/turns`),
  );
}

export async function getOutcome(sessionId: string): Promise<ConversationOutcome> {
  return jsonOrThrow(
    await fetch(`/api/v1/inbox/sessions/${encodeURIComponent(sessionId)}/outcome`),
  );
}

export async function saveOutcome(
  sessionId: string,
  draft: ConversationOutcomeDraft,
): Promise<ConversationOutcome> {
  return jsonOrThrow(
    await fetch(`/api/v1/inbox/sessions/${encodeURIComponent(sessionId)}/outcome`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(draft),
    }),
  );
}

export async function listCustomerCases(sessionId: string): Promise<CustomerCase[]> {
  return jsonOrThrow(
    await fetch(`/api/v1/inbox/sessions/${encodeURIComponent(sessionId)}/cases`),
  );
}

export async function createCustomerCase(
  sessionId: string,
  type: CustomerCaseType,
): Promise<CustomerCase> {
  return jsonOrThrow(
    await fetch(`/api/v1/inbox/sessions/${encodeURIComponent(sessionId)}/cases`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ type }),
    }),
  );
}

export async function claimWorkItem(sessionId: string): Promise<ConversationWorkItem> {
  return jsonOrThrow(
    await fetch(`/api/v1/inbox/sessions/${encodeURIComponent(sessionId)}/claim`, {
      method: "POST",
    }),
  );
}

export async function assignWorkItem(
  sessionId: string,
  assigneeAccountId: string | null,
): Promise<ConversationWorkItem> {
  return jsonOrThrow(
    await fetch(`/api/v1/inbox/sessions/${encodeURIComponent(sessionId)}/assignment`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ assigneeAccountId }),
    }),
  );
}

export async function changeWorkStatus(
  sessionId: string,
  status: WorkStatus,
): Promise<ConversationWorkItem> {
  return jsonOrThrow(
    await fetch(`/api/v1/inbox/sessions/${encodeURIComponent(sessionId)}/status`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status }),
    }),
  );
}

export async function changeWorkPriority(
  sessionId: string,
  priority: WorkPriority,
): Promise<ConversationWorkItem> {
  return jsonOrThrow(
    await fetch(`/api/v1/inbox/sessions/${encodeURIComponent(sessionId)}/priority`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ priority }),
    }),
  );
}
