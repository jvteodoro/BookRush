import assert from "node:assert/strict";
import { test } from "node:test";
import { fileURLToPath, URL } from "node:url";
import { mkdir } from "node:fs/promises";
import { createServer } from "vite";
import { chromium } from "playwright";
/* global document, localStorage */

test("book modal supports ratings, library actions, keyboard dismissal and responsive layouts", async () => {
  const root = fileURLToPath(new URL("../../", import.meta.url));
  const server = await createServer({
    root: `${root}/frontend`,
    configFile: `${root}/frontend/vite.config.ts`,
    server: { host: "127.0.0.1", port: 0, strictPort: false },
  });
  let browser;
  try {
    await server.listen();
    browser = await chromium.launch({
      channel: globalThis.process.env.PLAYWRIGHT_CHANNEL || "chrome",
    });
    const page = await browser.newPage({
      viewport: { width: 1440, height: 1100 },
    });
    await page.addInitScript(() =>
      localStorage.setItem("bookrush-theme", "light"),
    );
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    const url = `http://127.0.0.1:${server.httpServer.address().port}/tests/fixtures/book-details.html`;
    await page.goto(url);
    const modal = page.getByRole("dialog", { name: "Dom Casmurro" });
    await modal.waitFor();
    for (let step = 0; step < 12; step++) {
      await page.keyboard.press("Tab");
      assert.equal(
        await modal.evaluate((element) =>
          document.activeElement === document.body || element.contains(document.activeElement),
        ),
        true,
      );
    }
    await page
      .getByText("4,7 de 5 estrelas · 128 avaliações", { exact: true })
      .waitFor();
    await page.evaluate(() => document.fonts.ready);
    await mkdir(`${root}/frontend/dist/previews`, { recursive: true });
    await page.screenshot({
      path: `${root}/frontend/dist/previews/book-details-desktop.png`,
    });
    const bounds = await modal.boundingBox();
    assert.equal(Math.round(bounds.width), 960);
    assert.ok(Math.abs(bounds.x - (1440 - bounds.width) / 2) < 2);
    await modal.getByRole("button", { name: "Salvar na biblioteca" }).click();
    assert.equal(
      await modal
        .getByRole("button", { name: "Na sua biblioteca" })
        .getAttribute("aria-pressed"),
      "true",
    );
    await modal.getByRole("radio", { name: "5 estrelas", exact: true }).focus();
    await page.keyboard.press("Space");
    await modal
      .getByRole("button", { name: "Salvar avaliação", exact: true })
      .click();
    await modal.getByText("Avaliação salva.", { exact: true }).waitFor();
    await modal
      .getByText("Sua avaliação atual: 5 de 5 estrelas.", { exact: true })
      .waitFor();
    await modal
      .getByRole("button", { name: "Remover minha avaliação" })
      .click();
    await modal.getByText("Avaliação removida.", { exact: true }).waitFor();
    await page.keyboard.press("Escape");
    assert.equal(await modal.count(), 0);
    await page.getByRole("button", { name: "Ver livro", exact: true }).click();
    await modal.waitFor();
    await modal
      .getByRole("button", { name: "Fechar dom casmurro", exact: true })
      .click();
    assert.equal(
      await page
        .getByRole("button", { name: "Ver livro", exact: true })
        .evaluate((element) => element === document.activeElement),
      true,
    );
    await page.getByRole("button", { name: "Ver livro", exact: true }).click();
    await page.mouse.click(20, 20);
    assert.equal(await modal.count(), 0);
    for (const width of [390, 320]) {
      await page.setViewportSize({ width, height: 844 });
      await page
        .getByRole("button", { name: "Ver livro", exact: true })
        .click();
      await modal.waitFor();
      assert.equal(
        await modal.evaluate(
          (element) => element.scrollWidth <= element.clientWidth,
        ),
        true,
      );
      const box = await modal.boundingBox();
      assert.ok(box.x >= 0 && box.x + box.width <= width);
      await modal
        .getByRole("button", { name: "Salvar avaliação", exact: true })
        .scrollIntoViewIfNeeded();
      assert.equal(
        await modal
          .getByRole("button", { name: "Salvar avaliação", exact: true })
          .isVisible(),
        true,
      );
      if (width === 390) {
        await modal.evaluate((element) => (element.scrollTop = 0));
        await page.screenshot({
          path: `${root}/frontend/dist/previews/book-details-mobile.png`,
        });
        await page.evaluate(
          () => (document.documentElement.dataset.bookrushTheme = "dark"),
        );
        await page.screenshot({
          path: `${root}/frontend/dist/previews/book-details-dark.png`,
        });
      }
      await page.keyboard.press("Escape");
    }
    assert.deepEqual(errors, []);
  } finally {
    await browser?.close();
    await server.close();
  }
});
