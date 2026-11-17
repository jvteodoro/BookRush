import assert from "node:assert/strict";
import { test } from "node:test";
import { fileURLToPath, URL } from "node:url";
import { createServer } from "vite";
import { chromium } from "playwright";
import { mkdir } from "node:fs/promises";
/* global localStorage, document, getComputedStyle, innerWidth */

test("reader and admin share sidebar geometry, typography, navigation and reduced motion", async () => {
  const root = fileURLToPath(new URL("../..", import.meta.url));
  const reader = await createServer({
    plugins: [
      {
        name: "production-shell-fixture",
        configureServer(server) {
          server.middlewares.use(
            "/__production-shell",
            async (_request, response) => {
              response.setHeader("Content-Type", "text/html");
              response.end(
                await server.transformIndexHtml(
                  "/__production-shell",
                  '<html><head><link rel="stylesheet" href="/pageloop.css"></head><body><div id="root"></div><script type="module" src="/tests/fixtures/production-shell.tsx"></script></body></html>',
                ),
              );
            },
          );
        },
      },
    ],
    root: `${root}/frontend`,
    configFile: `${root}/frontend/vite.config.ts`,
    server: { host: "127.0.0.1", port: 0, strictPort: false },
  });
  const admin = await createServer({
    root: `${root}/admin-web`,
    configFile: `${root}/admin-web/vite.config.ts`,
    define: { "import.meta.env.VITE_USE_MOCKS": '"true"' },
    server: { host: "127.0.0.1", port: 0, strictPort: false },
  });
  let browser;
  try {
    await reader.listen();
    await admin.listen();
    const readerUrl = `http://127.0.0.1:${reader.httpServer.address().port}`;
    const adminUrl = `http://127.0.0.1:${admin.httpServer.address().port}`;
    browser = await chromium.launch({
      channel: globalThis.process.env.PLAYWRIGHT_CHANNEL || undefined,
    });
    const context = await browser.newContext({
      viewport: { width: 1440, height: 1000 },
    });
    await context.addInitScript(() =>
      localStorage.setItem("bookrush-theme", "light"),
    );
    const appPage = await context.newPage();
    const adminPage = await context.newPage();
    const errors = [];
    for (const page of [appPage, adminPage])
      page.on("pageerror", (error) => errors.push(error.message));
    await appPage.goto(`${readerUrl}/__production-shell`);
    await adminPage.goto(`${adminUrl}/catalog`);
    await appPage.locator("#legacy-sidebar").waitFor();
    assert.equal(await appPage.locator("#app .sidebar").count(), 0);
    await appPage
      .getByRole("button", { name: "Privacidade", exact: true })
      .first()
      .click();
    await appPage.waitForFunction(
      () =>
        document.getElementById("bookrush-sidebar-slot")?.dataset
          .activeRoute === "privacy",
    );
    await appPage
      .locator(
        '#legacy-sidebar [aria-current="page"][aria-label="Privacidade"]',
      )
      .waitFor();
    for (const page of [appPage, adminPage]) {
      await page.locator(".br-sidebar").waitFor();
      await page.evaluate(() => document.fonts.ready);
    }
    const measure = async (page, selector, properties) =>
      page
        .locator(selector)
        .first()
        .evaluate((element, keys) => {
          const style = getComputedStyle(element);
          return Object.fromEntries(keys.map((key) => [key, style[key]]));
        }, properties);
    for (const [selector, properties] of [
      [".br-sidebar", ["width", "padding", "borderRightWidth", "fontFamily"]],
      [
        ".br-sidebar__item",
        [
          "height",
          "padding",
          "gap",
          "fontFamily",
          "fontSize",
          "fontWeight",
          "lineHeight",
        ],
      ],
      [".br-sidebar__nav", ["gap", "marginTop"]],
      [
        ".br-sidebar__brand",
        ["fontFamily", "fontSize", "fontWeight", "lineHeight"],
      ],
      [
        ".br-nav-indicator",
        [
          "borderRadius",
          "backgroundColor",
          "transitionDuration",
          "transitionTimingFunction",
        ],
      ],
    ])
      assert.deepEqual(
        await measure(adminPage, selector, properties),
        await measure(appPage, selector, properties),
        selector,
      );
    assert.equal(
      await adminPage.locator(".br-sidebar__brand-copy small").textContent(),
      "Administração",
    );
    for (const width of [1024, 800, 1440]) {
      for (const page of [appPage, adminPage]) {
        await page.setViewportSize({ width, height: 1000 });
        await page.bringToFront();
        await page.waitForTimeout(350);
      }
      assert.deepEqual(
        await measure(adminPage, ".br-sidebar", ["width", "padding"]),
        await measure(appPage, ".br-sidebar", ["width", "padding"]),
      );
    }
    // Compare the same button variant and size under both application styles.
    const adminButton = adminPage.locator(".catalog-card .br-button").first();
    await adminButton.evaluate((el) =>
      el.classList.replace("br-button--sm", "br-button--md"),
    );
    const buttonProperties = [
      "fontFamily",
      "fontSize",
      "lineHeight",
      "padding",
      "borderWidth",
      "borderRadius",
      "boxShadow",
      "backgroundColor",
    ];
    assert.deepEqual(
      await measure(adminPage, ".br-button--primary", buttonProperties),
      await measure(appPage, ".br-button--primary", buttonProperties),
      "shared button",
    );
    await adminButton.evaluate((el) =>
      el.classList.replace("br-button--md", "br-button--sm"),
    );
    const screenshots = new URL("../artifacts/shared-ui/", import.meta.url);
    await mkdir(screenshots, { recursive: true });
    await appPage.screenshot({
      path: fileURLToPath(new URL("reader.png", screenshots)),
      fullPage: true,
    });
    await adminPage.screenshot({
      path: fileURLToPath(new URL("admin.png", screenshots)),
      fullPage: true,
    });
    await adminPage
      .getByRole("link", { name: "Analytics", exact: true })
      .first()
      .click();
    await adminPage.waitForTimeout(400);
    assert.equal(new URL(adminPage.url()).pathname, "/analytics");
    assert.ok(
      await adminPage
        .locator(".br-nav-indicator")
        .evaluate(
          (el) =>
            Math.abs(
              el.getBoundingClientRect().top -
                el.parentElement
                  .querySelector('[aria-current="page"]')
                  .getBoundingClientRect().top,
            ) < 1,
        ),
    );
    for (const page of [appPage, adminPage]) {
      await page.bringToFront();
      await page.locator(".br-sidebar").hover();
      await page
        .getByRole("button", { name: "Recolher menu", exact: true })
        .click();
      await page.waitForFunction(
        () =>
          document.querySelector(".br-sidebar").getBoundingClientRect()
            .width === 88,
        undefined,
        { timeout: 5000 },
      );
      assert.equal(
        await page.locator(".br-sidebar").getAttribute("data-collapsed"),
        "true",
        page.url(),
      );
      assert.equal(
        await page
          .locator(".br-sidebar")
          .evaluate((el) => el.getBoundingClientRect().width),
        88,
        page.url(),
      );
      await page
        .getByRole("button", { name: "Expandir menu", exact: true })
        .click();
      await page.emulateMedia({ reducedMotion: "reduce" });
      assert.ok(
        parseFloat(
          (await measure(page, ".br-nav-indicator", ["transitionDuration"]))
            .transitionDuration,
        ) <= 0.001,
      );
      await page.setViewportSize({ width: 390, height: 844 });
      assert.equal(await page.locator(".br-sidebar").isVisible(), false);
      assert.equal(await page.locator(".br-tabbar").isVisible(), true);
      assert.equal(
        await page.evaluate(
          () => document.documentElement.scrollWidth > innerWidth,
        ),
        false,
      );
    }
    await adminPage
      .getByRole("link", { name: "Catalog & Import", exact: true })
      .last()
      .click();
    await adminPage.locator(".catalog-card").first().waitFor();
    for (const width of [1440, 1024, 800, 390]) {
      await adminPage.setViewportSize({ width, height: 1000 });
      assert.equal(
        await adminPage.evaluate(
          () => document.documentElement.scrollWidth > innerWidth,
        ),
        false,
      );
      assert.ok(
        await adminPage
          .locator(".catalog-card")
          .evaluateAll((cards) =>
            cards.every(
              (card) =>
                card.scrollWidth <= card.clientWidth &&
                card.querySelector("button").getBoundingClientRect().bottom <
                  card.getBoundingClientRect().bottom,
            ),
          ),
      );
    }
    assert.deepEqual(errors, []);
  } finally {
    await browser?.close();
    await Promise.all([reader.close(), admin.close()]);
  }
});
