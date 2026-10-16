import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import { chromium } from "../../backstage/node_modules/playwright/index.mjs";

test("Annotation Studio supports keyboard ratings without exposing model scores", async () => {
  const server = spawn("npm", ["run", "dev", "--", "--host", "127.0.0.1", "--port", "4173"], {
    cwd: fileURLToPath(new globalThis.URL("..", import.meta.url)),
    env: { ...globalThis.process.env, VITE_USE_MOCKS: "true" },
    stdio: "ignore",
  });
  for (let attempt = 0; attempt < 50; attempt += 1) {
    try {
      await globalThis.fetch("http://127.0.0.1:4173/");
      break;
    } catch {
      await new Promise((resolve) => globalThis.setTimeout(resolve, 100));
    }
  }
  const browser = await chromium.launch({
    headless: true,
    executablePath: globalThis.process?.env.PLAYWRIGHT_CHROMIUM_PATH,
  });
  const page = await browser.newPage();
  await page.route("**/annotation/campaigns*", async (route) => {
    if (route.request().method() === "GET") {
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          items: [{ id: "campaign-1", name: "Pilot", status: "RUNNING" }],
        }),
      });
    } else {
      await route.continue();
    }
  });
  await page.route(
    "**/analytics/api/admin/v1/annotation/campaigns/campaign-1/claim-next",
    async (route) =>
      route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({ campaignItemId: "item-1" }),
      }),
  );
  await page.route(
    "**/analytics/api/admin/v1/annotation/items/item-1",
    async (route) =>
      route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          campaignItemId: "item-1",
          excerpt_text: "A blind excerpt.",
        }),
      }),
  );
  await page.route(
    "**/analytics/api/admin/v1/annotation/campaigns/campaign-1/progress",
    async (route) =>
      route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({ total: 100, completed: 0, remaining: 100 }),
      }),
  );

  await page.goto("http://127.0.0.1:4173/training");
  await page.getByRole("button", { name: "Annotation Studio" }).click();
  await page.getByLabel("Annotation campaign").selectOption("campaign-1");
  await page.getByRole("button", { name: "Claim next item" }).click();

  const hook = page.locator('[data-rating-code="FIRST_SCREEN_HOOK"]').first();
  await hook.focus();
  await page.keyboard.press("3");
  await page.waitForTimeout(50);
  assert.equal(
    await page
      .locator('[data-rating-code="FIRST_SCREEN_HOOK"][aria-checked="true"]')
      .count(),
    1,
  );
  assert.equal(await page.locator("[data-testid=model-score]").count(), 0);
  await browser.close();
  server.kill();
});
