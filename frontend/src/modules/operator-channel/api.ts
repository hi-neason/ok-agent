import i18n from "../../i18n";
import type { MyChannel, OperatorPresence, OperatorPresenceStatus } from "./types";

const BASE = "/api/v1/workbench/operator";

async function parse<T>(response: Response): Promise<T> {
  if (response.ok) return response.status === 204 ? (undefined as T) : response.json() as Promise<T>;
  let detail = "";
  try {
    const body = await (response.clone?.() ?? response).json();
    detail = body.detail || body.message || "";
  } catch {
    detail = await response.text().catch(() => "");
  }
  throw new Error(detail || i18n.t("common.requestFailed", { status: response.status }));
}

export async function fetchMyChannels(): Promise<MyChannel[]> {
  return parse<MyChannel[]>(await fetch(`${BASE}/channels`));
}

export async function fetchPresence(): Promise<OperatorPresence> {
  return parse<OperatorPresence>(await fetch(`${BASE}/presence`));
}

export async function updatePresence(status: OperatorPresenceStatus): Promise<OperatorPresence> {
  return parse<OperatorPresence>(await fetch(`${BASE}/presence`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ status }),
  }));
}
