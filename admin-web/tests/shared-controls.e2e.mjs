import assert from "node:assert/strict";
import { test } from "node:test";
import { fileURLToPath, URL } from "node:url";
import { createServer } from "vite";
import { chromium } from "playwright";
/* global document, getComputedStyle */

test("admin header has only title and theme; local search and theme persist", async () => {
  const root = fileURLToPath(new URL("..", import.meta.url));
  const server = await createServer({
    root,
    configFile: `${root}/vite.config.ts`,
    define: { "import.meta.env.VITE_USE_MOCKS": '"true"' },
    server: { host: "127.0.0.1", port: 0, strictPort: false },
  });
  let browser;
  try {
    await server.listen();
    browser = await chromium.launch({
      channel: globalThis.process.env.PLAYWRIGHT_CHANNEL || undefined,
    });
    const page = await browser.newPage();
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/`);
    await page.getByRole("button", { name: "Ativar tema escuro" }).waitFor();
    assert.equal(
      await page
        .locator(
          ".br-header input, .br-header .br-home-shortcut, .br-header .br-avatar",
        )
        .count(),
      0,
    );
    await page.getByRole("button", { name: "Ativar tema escuro" }).click();
    await page.waitForFunction(
      () => document.documentElement.dataset.bookrushTheme === "dark",
    );
    await page.reload();
    await page.getByRole("button", { name: "Ativar tema claro" }).waitFor();
    await page.getByRole("button", { name: "Ativar tema claro" }).click();
    await page
      .getByRole("link", { name: "Catalog & Import", exact: true })
      .first()
      .click();
    await page
      .getByRole("textbox", { name: "Buscar título ou autor" })
      .fill("Jane Austen");
    await page.locator(".catalog-card").first().waitFor();
    await page.waitForFunction(
      () => document.querySelectorAll(".catalog-card").length === 1,
    );
    assert.equal(await page.locator(".catalog-card").count(), 1);
    assert.match(
      await page.locator(".catalog-card").innerText(),
      /Jane Austen/,
    );
    await page
      .getByRole("textbox", { name: "Buscar título ou autor" })
      .fill("não existe");
    await page
      .getByRole("heading", { name: "Nenhuma obra nesta página" })
      .waitFor();
    assert.equal(await page.locator(".catalog-card").count(), 0);
    await page
      .getByRole("textbox", { name: "Buscar título ou autor" })
      .fill("Mary Shelley");
    await page.locator(".catalog-card").first().waitFor();
    assert.match(
      await page.locator(".catalog-card").innerText(),
      /Frankenstein/,
    );
    await page.getByRole("textbox", { name: "Buscar título ou autor" }).click();
    assert.equal(
      await page
        .locator(".br-search-control")
        .evaluate((el) => getComputedStyle(el).outlineStyle),
      "none",
    );
    await page
      .getByRole("link", { name: "Review Queue", exact: true })
      .first()
      .click();
    const sort = page.getByLabel("Ordenar por");
    await sort.selectOption("title");
    assert.equal(
      await sort.evaluate((el) => getComputedStyle(el).outlineStyle),
      "none",
    );
    assert.equal(
      await sort.evaluate((el) => getComputedStyle(el).boxShadow),
      "none",
    );
    assert.deepEqual(errors, []);
  } finally {
    await browser?.close();
    await server.close();
  }
});
