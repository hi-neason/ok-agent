import type {
  AgentProductBinding,
  Product,
  ProductDraft,
  ProductSource,
  ProductSourceDraft,
  Solution,
  SolutionDraft,
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

const jsonHeaders = { "Content-Type": "application/json" };

/** Parse a newline/comma separated string into a trimmed, de-duplicated list. */
function splitList(raw: string): string[] {
  return [
    ...new Set(
      raw
        .split(/[\n,，]/)
        .map((s) => s.trim())
        .filter(Boolean),
    ),
  ];
}

/** Parse a JSON object text, tolerant of blank input. Throws with a friendly message. */
function parseJsonObject(raw: string, label: string): Record<string, unknown> {
  const text = raw.trim();
  if (!text) return {};
  try {
    const parsed = JSON.parse(text);
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) return parsed;
    throw new Error("not an object");
  } catch {
    throw new Error(`${label} 必须是合法的 JSON 对象（如 {"key":"value"}）`);
  }
}

function productPayload(draft: ProductDraft) {
  return {
    productKey: draft.productKey.trim(),
    name: draft.name.trim(),
    brand: draft.brand.trim(),
    category: draft.category.trim(),
    priceMin: draft.priceMin === null || Number.isNaN(draft.priceMin) ? null : draft.priceMin,
    priceMax: draft.priceMax === null || Number.isNaN(draft.priceMax) ? null : draft.priceMax,
    currency: draft.currency.trim() || "CNY",
    spec: parseJsonObject(draft.spec, "规格(spec)"),
    sellingPoints: draft.sellingPoints.trim() || null,
    scenarioTags: splitList(draft.scenarioTags),
    imageUrls: splitList(draft.imageUrls),
    description: draft.description.trim() || null,
    status: draft.status,
    weight: Math.round(draft.weight),
  };
}

function sourcePayload(draft: ProductSourceDraft) {
  const secrets = parseJsonObject(draft.secretsJson, "凭据(secrets)") as Record<string, string>;
  return {
    sourceKey: draft.sourceKey.trim(),
    name: draft.name.trim(),
    sourceType: draft.sourceType,
    baseUrl: draft.baseUrl.trim(),
    configJson: draft.configJson.trim() || "{}",
    // Drop empty secret values so the backend keeps the stored secret on update.
    secrets: Object.fromEntries(Object.entries(secrets).filter(([, v]) => String(v).trim() !== "")),
  };
}

// ---- Products ----

export async function listProducts(
  page = 0,
  size = 20,
): Promise<Page<Product>> {
  return jsonOrThrow<Page<Product>>(
    await fetch(`/api/v1/products?page=${page}&size=${size}`),
  );
}

export async function createProduct(draft: ProductDraft): Promise<Product> {
  const res = await fetch("/api/v1/products", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify(productPayload(draft)),
  });
  return jsonOrThrow<Product>(res);
}

export async function updateProduct(id: string, draft: ProductDraft): Promise<Product> {
  const res = await fetch(`/api/v1/products/${id}`, {
    method: "PUT",
    headers: jsonHeaders,
    body: JSON.stringify(productPayload(draft)),
  });
  return jsonOrThrow<Product>(res);
}

export async function deleteProduct(id: string): Promise<void> {
  return jsonOrThrow<void>(await fetch(`/api/v1/products/${id}`, { method: "DELETE" }));
}

export async function setProductStatus(
  id: string,
  status: Product["status"],
): Promise<Product> {
  const res = await fetch(`/api/v1/products/${id}/status?status=${status}`, {
    method: "PATCH",
  });
  return jsonOrThrow<Product>(res);
}

// ---- Solutions ----

export async function listSolutions(
  page = 0,
  size = 20,
): Promise<Page<Solution>> {
  return jsonOrThrow<Page<Solution>>(
    await fetch(`/api/v1/solutions?page=${page}&size=${size}`),
  );
}

export async function createSolution(draft: SolutionDraft): Promise<Solution> {
  const res = await fetch("/api/v1/solutions", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify({ ...draft, items: draft.items.filter((it) => it.productId) }),
  });
  return jsonOrThrow<Solution>(res);
}

export async function updateSolution(id: string, draft: SolutionDraft): Promise<Solution> {
  const res = await fetch(`/api/v1/solutions/${id}`, {
    method: "PUT",
    headers: jsonHeaders,
    body: JSON.stringify({ ...draft, items: draft.items.filter((it) => it.productId) }),
  });
  return jsonOrThrow<Solution>(res);
}

export async function deleteSolution(id: string): Promise<void> {
  return jsonOrThrow<void>(await fetch(`/api/v1/solutions/${id}`, { method: "DELETE" }));
}

export async function setSolutionStatus(
  id: string,
  status: Solution["status"],
): Promise<Solution> {
  const res = await fetch(`/api/v1/solutions/${id}/status?status=${status}`, {
    method: "PATCH",
  });
  return jsonOrThrow<Solution>(res);
}

// ---- Product sources ----

export async function listProductSources(
  page = 0,
  size = 20,
): Promise<Page<ProductSource>> {
  return jsonOrThrow<Page<ProductSource>>(
    await fetch(`/api/v1/product-sources?page=${page}&size=${size}`),
  );
}

export async function createProductSource(draft: ProductSourceDraft): Promise<ProductSource> {
  const res = await fetch("/api/v1/product-sources", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify(sourcePayload(draft)),
  });
  return jsonOrThrow<ProductSource>(res);
}

export async function updateProductSource(
  id: string,
  draft: ProductSourceDraft,
): Promise<ProductSource> {
  const res = await fetch(`/api/v1/product-sources/${id}`, {
    method: "PUT",
    headers: jsonHeaders,
    body: JSON.stringify(sourcePayload(draft)),
  });
  return jsonOrThrow<ProductSource>(res);
}

export async function deleteProductSource(id: string): Promise<void> {
  return jsonOrThrow<void>(
    await fetch(`/api/v1/product-sources/${id}`, { method: "DELETE" }),
  );
}

export async function setProductSourceEnabled(
  id: string,
  value: boolean,
): Promise<ProductSource> {
  const res = await fetch(`/api/v1/product-sources/${id}/enabled?value=${value}`, {
    method: "PATCH",
  });
  return jsonOrThrow<ProductSource>(res);
}

export async function testProductSource(id: string): Promise<ProductSource> {
  return jsonOrThrow<ProductSource>(
    await fetch(`/api/v1/product-sources/${id}/test`, { method: "POST" }),
  );
}

export async function syncProductSource(id: string): Promise<{ upserted: number }> {
  return jsonOrThrow<{ upserted: number }>(
    await fetch(`/api/v1/product-sources/${id}/sync`, { method: "POST" }),
  );
}

// ---- Agent product binding ----

export async function getAgentProductBinding(
  agentId: string,
): Promise<AgentProductBinding | null> {
  const res = await fetch(`/api/v1/agents/${agentId}/products`);
  if (!res.ok) {
    if (res.status === 404) return null;
    return jsonOrThrow<AgentProductBinding>(res);
  }
  // Backend returns 200 with an empty body when no binding exists yet.
  const text = await res.text();
  if (!text.trim()) return null;
  return JSON.parse(text) as AgentProductBinding;
}

export async function upsertAgentProductBinding(
  agentId: string,
  binding: {
    scope: AgentProductBinding["scope"];
    scopeValue: string | null;
    capabilities: AgentProductBinding["capabilities"];
    enabled: boolean;
  },
): Promise<AgentProductBinding> {
  const res = await fetch(`/api/v1/agents/${agentId}/products`, {
    method: "PUT",
    headers: jsonHeaders,
    body: JSON.stringify(binding),
  });
  return jsonOrThrow<AgentProductBinding>(res);
}

export async function deleteAgentProductBinding(agentId: string): Promise<void> {
  return jsonOrThrow<void>(
    await fetch(`/api/v1/agents/${agentId}/products`, { method: "DELETE" }),
  );
}
