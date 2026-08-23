import type { ModelApiItem, ModelItem } from "./types";
import type { Page } from "../shared";

export async function fetchModels(
  page = 0,
  size = 20,
): Promise<Page<ModelItem>> {
  const response = await fetch(`/api/v1/models?page=${page}&size=${size}`);
  if (!response.ok) {
    return { content: [], totalElements: 0, totalPages: 0, number: page, size };
  }
  const data = (await response.json()) as Page<ModelApiItem>;
  return {
    ...data,
    content: data.content.map((item) => ({
      ...item,
      apiKey: "",
      updated: new Date(item.updatedAt).toLocaleString(),
    })),
  };
}

export async function saveModel(model: ModelItem): Promise<ModelItem> {
  const existing = Boolean(model.id);
  const response = await fetch(
    existing ? `/api/v1/models/${model.id}` : "/api/v1/models",
    {
      method: existing ? "PUT" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(model),
    },
  );
  if (!response.ok) throw new Error("save failed");
  return (await response.json()) as ModelItem;
}

export type RawConnectionResult = {
  success?: boolean;
  statusCode?: number;
  message?: string;
  detail?: string;
  title?: string;
};

export async function requestConnectionTest(
  editing: ModelItem,
): Promise<{ ok: boolean; status: number; result: RawConnectionResult }> {
  const useSavedCredential = Boolean(editing.id && !editing.apiKey.trim());
  const response = await fetch(
    useSavedCredential
      ? `/api/v1/models/${editing.id}/test-connection`
      : "/api/v1/models/test-connection",
    {
      method: "POST",
      ...(useSavedCredential
        ? {}
        : {
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(editing),
          }),
    },
  );
  const responseText = await response.text();
  let result: RawConnectionResult = {};
  try {
    result = responseText ? JSON.parse(responseText) : {};
  } catch {
    result = { message: responseText };
  }
  return { ok: response.ok, status: response.status, result };
}
