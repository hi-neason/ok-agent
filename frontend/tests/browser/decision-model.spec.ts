import { test, expect, type Page } from "@playwright/test";

const paged = (content: unknown[]) => ({ content, totalPages: 1, totalElements: content.length, number: 0, size: 100 });
const models = [
  { id: "llm-1", name: "Chat model", type: "LLM", provider: "OpenAI", modelId: "chat-test", enabled: true },
  { id: "decision-1", name: "JEV routing", type: "DECISION", provider: "JEV", modelId: "jev-latest", enabled: true },
  { id: "disabled", name: "Disabled decision", type: "DECISION", provider: "JEV", modelId: "jev-latest", enabled: false },
  { id: "vision-1", name: "Vision model", type: "VISION", provider: "Vision", modelId: "vision-test", enabled: true },
];
const agent = { id: "agent-1", agentKey: "support", name: "Support", enabled: true,
  modelAssetId: "llm-1", decisionModelAssetId: null, systemPrompt: "Help customers.",
  mcpServerIds: [], skillIds: [], permissionMode: "BYPASS", workspaceMode: "DISABLED", shellEnabled: false };

async function setup(page: Page) {
  const unexpected: string[] = [];
  const errors: string[] = [];
  const savedModels: Record<string, unknown>[] = [];
  const savedConfigs: Record<string, unknown>[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.addInitScript(() => {
    sessionStorage.setItem("ok-agent.access-token", "test-only-token");
    localStorage.setItem("ok-agent.locale", "zh-CN");
  });
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    const method = route.request().method();
    let data: unknown;
    if (path === "/api/v1/auth/me") data = { id: "operator", username: "operator", role: "ADMIN" };
    else if (path === "/api/v1/models" && method === "POST") {
      const payload = route.request().postDataJSON() as Record<string, unknown>;
      savedModels.push(payload);
      data = { ...payload, id: "created-decision", apiKey: undefined, apiKeyConfigured: true };
    } else if (path === "/api/v1/models") data = paged(models);
    else if (path === "/api/v1/agents/agent-1") data = agent;
    else if (["/api/v1/mcp-servers", "/api/v1/skills", "/api/v1/users", "/api/v1/observe/sessions"].includes(path)) data = paged([]);
    else if (path === "/api/v1/agents/agent-1/configuration/validate") data = { valid: true, errors: [], warnings: [] };
    else if (path === "/api/v1/agents/agent-1/configuration" && method === "PUT") {
      const payload = route.request().postDataJSON() as Record<string, unknown>;
      savedConfigs.push(payload);
      data = { ...agent, ...payload };
    } else {
      unexpected.push(`${method} ${path}`);
      return route.fulfill({ status: 500, json: { message: "Unmocked test endpoint" } });
    }
    await route.fulfill({ json: { success: true, code: "OK", message: "OK", data } });
  });
  return { savedModels, savedConfigs, check: () => {
    expect(unexpected).toEqual([]);
    expect(errors).toEqual([]);
  } };
}

test("creates a decision model using the JEV protocol defaults", async ({ page }) => {
  const state = await setup(page);
  await page.goto("/agent/models");
  await page.getByRole("button", { name: /新增模型/ }).click();
  const dialog = page.getByRole("dialog", { name: "模型配置" });
  await dialog.getByRole("button", { name: "决策模型", exact: true }).click();
  await expect(dialog.getByLabel("模型提供商", { exact: true })).toHaveValue("JEV");
  await expect(dialog.getByLabel("模型（MODEL_ID）", { exact: true })).toHaveValue("jev-latest");
  await expect(dialog.getByLabel("服务地址（BASE_URL）", { exact: true })).toHaveValue("https://api.typesafe.ai/v1/systemone");
  await dialog.getByLabel("名称", { exact: true }).fill("Routing decision");
  await dialog.getByLabel("API 密钥（API_KEY）", { exact: true }).fill("test-only-credential");
  await dialog.getByRole("button", { name: "保存模型", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  expect(state.savedModels).toHaveLength(1);
  expect(state.savedModels[0]).toMatchObject({ name: "Routing decision", type: "DECISION", provider: "JEV",
    modelId: "jev-latest", endpoint: "https://api.typesafe.ai/v1/systemone", apiKey: "test-only-credential" });
  state.check();
});

test("agent separates decision and chat model choices and saves the decision reference", async ({ page }) => {
  const state = await setup(page);
  await page.goto("/agent/agents/agent-1/config");
  const chat = page.locator('select[data-field="modelAssetId"]');
  const decision = page.locator('[data-field="decisionModelAssetId"] select');
  await expect(chat).toHaveValue("llm-1");
  await expect(chat.locator("option")).toHaveCount(2);
  await expect(chat.locator('option[value="llm-1"]')).toContainText("Chat model");
  await expect(decision.locator("option")).toHaveCount(2);
  await expect(decision.locator('option[value="decision-1"]')).toContainText("JEV routing");
  await decision.selectOption("decision-1");
  await page.getByRole("button", { name: "保存配置", exact: true }).click();
  await expect(page.getByText("配置已保存", { exact: false })).toBeVisible();
  expect(state.savedConfigs).toHaveLength(1);
  expect(state.savedConfigs[0]).toMatchObject({ modelAssetId: "llm-1", decisionModelAssetId: "decision-1" });
  state.check();
});
