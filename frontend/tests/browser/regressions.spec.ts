import { test, expect, type Page } from "@playwright/test";

const agent = { id: "agent-1", agentKey: "superman", name: "SuperMan", enabled: true,
  personaInjectionMode: "SELF_ONLY", personaExtractEnabled: true };
const user = { id: "user-1", userId: "customer-1", username: "customer", displayName: "Test Customer" };
const persona = { userId: user.userId, agentId: agent.id, summary: "Prefers quiet trips",
  tags: ["travel"], preferences: {}, facts: "", memory: "", updatedAt: "2026-01-01T00:00:00Z" };
const paged = (content: unknown[]) => ({ content, totalPages: 1, totalElements: content.length, number: 0, size: 100 });

async function setup(page: Page, failAgents = false) {
  let storedPersona = { ...persona };
  const unexpected: string[] = [];
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.addInitScript(() => {
    sessionStorage.setItem("ok-agent.access-token", "test-only-token");
    localStorage.setItem("ok-agent.locale", "zh-CN");
  });
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    let data: unknown;
    if (path === "/api/v1/auth/me") data = { ...user, role: "ADMIN" };
    else if (path === "/api/v1/agents") {
      if (failAgents) return route.fulfill({ status: 400, json: { success: false, message: "Test agent load failed", code: "VALIDATION_ERROR" } });
      expect(Number(url.searchParams.get("size"))).toBeLessThanOrEqual(100);
      data = paged([agent]);
    } else if (path === "/api/v1/channels") data = paged([]);
    else if (/\/agents\/[^/]+\/(versions|releases)$/.test(path)) data = [];
    else if (path === "/api/v1/users") data = paged([user]);
    else if (path === "/api/v1/persona/coverage") data = { [user.userId]: [agent.id] };
    else if (path.endsWith("/injection-preview")) data = { mode: "SELF_ONLY", block: "Prefers quiet trips" };
    else if (path === "/api/v1/persona/users/customer-1") data = [storedPersona];
    else if (path === "/api/v1/persona/users/customer-1/agents/agent-1") {
      if (route.request().method() === "PUT") storedPersona = { ...storedPersona, ...route.request().postDataJSON() };
      data = storedPersona;
    }
    else {
      unexpected.push(path);
      return route.fulfill({ status: 500, json: { message: "Unmocked test endpoint" } });
    }
    await route.fulfill({ json: { success: true, code: "OK", message: "OK", data } });
  });
  return () => {
    expect(unexpected).toEqual([]);
    expect(errors).toEqual([]);
  };
}

test("release page loads agents from the real response envelope", async ({ page }) => {
  const check = await setup(page);
  await page.goto("/agent/releases");
  await expect(page.getByRole("combobox").first()).toHaveValue(agent.id);
  await expect(page.getByRole("combobox").first().locator("option")).toContainText("SuperMan");
  await page.reload();
  await expect(page.getByRole("combobox").first()).toHaveValue(agent.id);
  check();
});

test("release page exposes API errors instead of silently empty options", async ({ page }) => {
  const check = await setup(page, true);
  await page.goto("/agent/releases");
  await expect(page.getByText("Test agent load failed", { exact: false })).toBeVisible();
  check();
});

test("persona page opens stored profile and selects a preview agent", async ({ page }) => {
  const check = await setup(page);
  await page.goto("/workbench/personas");
  await expect(page.locator(".persona-table").getByText("Test Customer", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: /查看/ }).first().click();
  await expect(page.locator(".persona-preview-agent select")).toHaveValue(agent.id);
  await expect(page.locator(".persona-preview-block")).toContainText("Prefers quiet trips");
  await expect(page.locator(".persona-detail")).toBeVisible();
  await expect(page.locator(".persona-detail input").first()).toHaveValue("Prefers quiet trips");
  await page.locator(".persona-detail input").first().fill("Updated preference");
  await page.locator(".persona-detail").getByRole("button", { name: /保存/ }).click();
  await expect(page.locator(".persona-notice.ok")).toBeVisible();
  await page.getByRole("button", { name: /返回列表/ }).click();
  await page.getByRole("button", { name: /查看/ }).first().click();
  await expect(page.locator(".persona-detail input").first()).toHaveValue("Updated preference");
  check();
});
