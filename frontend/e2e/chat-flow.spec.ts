import { expect, test } from "@playwright/test";

import { installMockApi, loginThroughUi } from "./support/mockApi";

test.beforeEach(async ({ context }) => {
  await installMockApi(context, "admin");
});

test("sends a chat message and renders a structured response without fatal errors", async ({ page }) => {
  await loginThroughUi(page);

  const chatInput = page.getByLabel("发送消息").first();
  await chatInput.fill("请帮我总结最近一次版本发布计划。");
  await page.getByRole("button", { name: "发送消息" }).click();

  await expect(page.getByText("请帮我总结最近一次版本发布计划。", { exact: true })).toBeVisible();
  await expect(page.getByText("这是一个稳定的模拟回答。")).toBeVisible();
  await expect(page.getByRole("button", { name: "发送消息" })).toBeVisible({ timeout: 15000 });
  await expect(page.getByRole("button", { name: "复制内容" })).toBeVisible({ timeout: 15000 });
  await expect(page.getByRole("button", { name: "点赞" })).toBeVisible({ timeout: 15000 });
  await expect(page.getByRole("banner").getByText("关于发布计划的提问")).toBeVisible();
  await expect(page.getByText("生成已中断。")).toHaveCount(0);
});
