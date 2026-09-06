import { test, expect } from "@playwright/test";

test("inbox requests one customer page, loads older messages and resumes automation", async ({ page }) => {
  const requestedPages: string[] = [];
  const unexpected: string[] = [];
  const errors: string[] = [];
  let resumed = false;
  const session = (id: string) => ({ sessionId: id, agentId: "agent", agentName: "Support", title: `Conversation ${id}`,
    userId: id, customerName: `Customer ${id}`, status: resumed ? "OPEN" : "WAITING_HUMAN", priority: "NORMAL",
    assigneeAccountId: null, assigneeName: null, channelType: "FEISHU", turnCount: 2, version: 0,
    createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z" });
  page.on("pageerror", (error) => errors.push(error.message));
  await page.addInitScript(() => {
    sessionStorage.setItem("ok-agent.access-token", "test-only-token");
    localStorage.setItem("ok-agent.locale", "zh-CN");
  });
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    let data: unknown;
    if (path === "/api/v1/auth/me") data = { id: "operator", username: "operator", displayName: "Operator", role: "ADMIN" };
    else if (path === "/api/v1/workbench/sessions/customers") {
      requestedPages.push(url.searchParams.get("page") ?? "");
      data = { sessions: [session(url.searchParams.get("page") === "0" ? "a" : "b")], totalCustomers: 21, totalPages: 2 };
    } else if (path.endsWith("/operators")) data = [];
    else if (path === "/api/v1/workbench/metrics") data = { totalConversations: 21, waitingHuman: 21, inProgress: 0,
      resolved: 0, leads: 0, tickets: 0, satisfactionResponses: 0, averageSatisfaction: null };
    else if (path.endsWith("/message-window")) {
      const seq = url.searchParams.get("beforeSeq") === "2" ? 1 : 2;
      data = [{ id: seq, sessionId: "a", seq, role: "user", content: seq === 1 ? "Earlier message" : "Latest message", createdAt: "2026-01-01T00:00:00Z" }];
    } else if (path.endsWith("/outcome")) data = { summary: "", customerNeed: "", sentiment: "UNKNOWN" };
    else if (path.endsWith("/cases")) data = [];
    else if (path.endsWith("/satisfaction")) data = null;
    else if (path.endsWith("/resume-automation")) { resumed = true; data = session("a"); }
    else { unexpected.push(path); return route.fulfill({ status: 500, json: { message: "Unmocked test request" } }); }
    await route.fulfill({ json: { success: true, code: "OK", message: "OK", data } });
  });
  await page.goto("/workbench/inbox");
  await expect(page.getByText("Latest message", { exact: true })).toBeVisible();
  // React StrictMode may repeat the same mount request in the development server.
  expect([...new Set(requestedPages)]).toEqual(["0"]);
  await page.getByRole("button", { name: "加载更早消息" }).click();
  await expect(page.getByText("Earlier message", { exact: true })).toBeVisible();
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "恢复机器人接待", exact: true }).click();
  await expect.poll(() => resumed).toBe(true);
  await expect(page.getByRole("button", { name: "恢复机器人接待", exact: true })).toHaveCount(0);
  await page.locator(".pagination-bar").getByRole("button").last().click();
  await expect(page.getByText("Customer b", { exact: true }).first()).toBeVisible();
  expect([...new Set(requestedPages)]).toEqual(["0", "1"]);
  expect(unexpected).toEqual([]);
  expect(errors).toEqual([]);
});
