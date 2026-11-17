import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import { chromium } from "../../backstage/node_modules/playwright/index.mjs";
import fixture from "./fixtures/dashboard-redesign-v2.json" with { type: "json" };

const root = fileURLToPath(new URL("..", import.meta.url));
const areas = [
  ["business", "Business Overview"],
  ["operations", "Operations"],
  ["recommendations", "Recommendation Intelligence"],
  ["content", "Content Intelligence"],
  ["factory", "Analytics Factory"],
  ["quality", "Data Quality"],
];
const endpoints = {
  business: ["weekly-significant-readers", "reading-daily", "retention-cohort", "book-engagement-trends", "feed-daily"],
  operations: ["health"],
  recommendations: ["feed-daily", "recommendation-daily", "recommendation-quality-daily", "recommendation-position-daily", "recommendation-position-funnel-daily", "excerpt-performance"],
  content: ["content-supply", "rights-status-daily", "content-coverage", "content-rank-scores", "excerpt-production"],
  factory: ["backlog", "ingestion-queue", "pipeline-phases", "ingestion-daily", "pipeline"],
  quality: ["health", "events", "attribution", "reconciliation", "access-audit"],
};

test("Dashboard Redesign V2 loads only selected area and renders six deterministic screenshots", async () => {
  const server = spawn("npm", ["run", "dev", "--", "--host", "127.0.0.1", "--port", "4176"], {
    cwd: root, env: { ...process.env, VITE_USE_MOCKS: "true" }, stdio: "ignore",
  });
  let browser;
  try {
    for (let i = 0; i < 60; i += 1) {
      try { await fetch("http://127.0.0.1:4176/"); break; } catch { await new Promise(resolve => setTimeout(resolve, 150)); }
    }
    browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_PATH });
    const page = await browser.newPage({ viewport: { width: 1440, height: 1100 }, deviceScaleFactor: 1 });
    const requested = [];
    await page.route("**/api/admin/v1/reporting/**", async route => {
      const url = new URL(route.request().url());
      const name = url.pathname.split("/").at(-1);
      requested.push(name);
      const body = name === "health" ? fixture.health : { ...fixture.default, metricId: name };
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
    });
    await page.goto("http://127.0.0.1:4176/dashboards?area=business");
    await page.getByRole("heading", { name: "Business Overview" }).waitFor();
    await page.locator(".dashboard-v2-kpi strong").first().waitFor();
    assert.match(await page.locator(".dashboard-v2-kpi strong").first().innerText(), /84/);
    assert.deepEqual([...new Set(requested)].sort(), [...endpoints.business].sort());
    await page.screenshot({ path: `${root}/docs/dashboard-redesign-v2/screenshots/business-overview.png`, fullPage: true });
    for (const [area, title] of areas.slice(1)) {
      await page.getByRole("button", { name: title, exact: true }).click();
      await page.getByRole("heading", { name: title }).waitFor();
      await page.getByText("O que exige atenção?").waitFor();
      await page.screenshot({ path: `${root}/docs/dashboard-redesign-v2/screenshots/${area}.png`, fullPage: true });
      const fetched = new Set(requested);
      assert.deepEqual([...fetched].sort(), [...new Set(areas.slice(0, areas.findIndex(([key]) => key === area) + 1).flatMap(([key]) => endpoints[key]))].sort());
    }
    assert.equal(await page.getByText(/EX-\d|FD-\d|OP-\d/).count(), 0);
  } finally {
    await browser?.close();
    server.kill();
  }
});
