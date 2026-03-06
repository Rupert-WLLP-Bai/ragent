import { expect, test } from "@playwright/test";

import { installMockApi, loginThroughUi } from "./support/mockApi";

test.beforeEach(async ({ context }) => {
  await installMockApi(context, "admin");
});

test("logs in and lands on the chat UI", async ({ page }) => {
  await loginThroughUi(page);

  await expect(page).toHaveURL(/\/chat(?:\/.*)?$/);
  await expect(page.getByText("RAG 智能问答").first()).toBeVisible();
  await expect(page.getByRole("button", { name: "发送消息" })).toBeVisible();
  await expect(page.getByText("历史会话")).toBeVisible();
});

test("opens the admin area from the chat sidebar", async ({ page }) => {
  await loginThroughUi(page);

  await page.getByRole("button", { name: "管理后台" }).click();

  await expect(page).toHaveURL(/\/admin(?:\/dashboard)?$/);
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
  await expect(page.getByText("趋势分析")).toBeVisible();
  await expect(page.getByRole("button", { name: "返回聊天" })).toBeVisible();
});
