import assert from "node:assert/strict";
import { createServer } from "node:http";
import { createRequire } from "node:module";
import { fileURLToPath, URL } from "node:url";
import process from "node:process";
import console from "node:console";
/* global window, document, NodeFilter, innerHeight, scrollTo, scrollY */
import { build } from "esbuild";

// Reuse the repository's browser test dependency; no production dependency.
const { chromium } = createRequire(
  new URL("../../../admin-web/package.json", import.meta.url),
)("playwright");
const bundle = await build({
  stdin: {
    contents: `import { readiumBridge } from './src/reader/readiumBridge.ts'; window.reader = readiumBridge;`,
    resolveDir: fileURLToPath(new URL("../../", import.meta.url)),
    loader: "ts",
  },
  bundle: true,
  write: false,
  format: "esm",
  platform: "browser",
  plugins: [
    {
      name: "fixture-auth",
      setup(builder) {
        builder.onResolve({ filter: /\/auth$/ }, () => ({
          path: "auth",
          namespace: "fixture",
        }));
        builder.onLoad({ filter: /.*/, namespace: "fixture" }, () => ({
          contents: `export const authClient = {fetch: (url, init) => fetch(url, init)};`,
          loader: "js",
        }));
      },
    },
  ],
});
const book = "11111111-1111-1111-1111-111111111111";
const chapterPath = `/api/v1/reader/books/${book}/versions/v1/chapters/c1/content`;
const text =
  "Cada trecho precisa reaparecer no mesmo lugar quando a leitura é retomada. A tipografia altera as quebras de linha, mas não deve pular palavras ou parágrafos. ".repeat(
    9,
  );
const content = `<!DOCTYPE html><html lang="pt"><head><title>Regression</title><style>body{font-size:18px;line-height:1.5}p{margin:1.25em 0}</style></head><body><h1>Layout regression</h1>${Array.from({ length: 150 }, (_, n) => `<p id="paragraph-${n}">Parágrafo ${n}. ${text}</p>`).join("")}</body></html>`;
let saved;
const server = createServer(async (request, response) => {
  response.setHeader("Cache-Control", "no-store");
  const url = request.url;
  if (url === "/") {
    response.setHeader("Content-Type", "text/html");
    response.end(
      `<html><head><style>body{margin:0}#reader{position:fixed;inset:0}#reader iframe{display:block;width:100%;height:100%;min-height:100vh;border:0}</style></head><body><div id="reader"></div><script type="module" src="/fixture.js"></script></body></html>`,
    );
  } else if (url === "/fixture.js") {
    response.setHeader("Content-Type", "text/javascript");
    response.end(bundle.outputFiles[0].text);
  } else if (url === chapterPath) {
    response.setHeader("Content-Type", "text/html");
    response.end(content);
  } else {
    response.setHeader("Content-Type", "application/json");
    if (url.endsWith("publication.json"))
      response.end(
        JSON.stringify({
          metadata: {
            title: "Regression",
            identifier: "regression",
            language: "pt",
          },
          links: [
            {
              rel: "self",
              href: `http://${request.headers.host}${url}`,
              type: "application/webpub+json",
            },
          ],
          readingOrder: [
            { href: chapterPath, type: "text/html", title: "Chapter 1" },
          ],
        }),
      );
    else if (url.endsWith("/progress") && request.method === "PUT") {
      let body = "";
      for await (const part of request) body += part;
      saved = JSON.parse(body).locator;
      response.end("{}");
    } else if (url.endsWith("/progress"))
      response.end(
        JSON.stringify(saved ? [{ locator: JSON.stringify(saved) }] : []),
      );
    else
      response.end(
        JSON.stringify({ id: "22222222-2222-2222-2222-222222222222" }),
      );
  }
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
let browser;
try {
  browser = await chromium.launch({
    headless: true,
    executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH,
  });
  const page = await browser.newPage({
    viewport: { width: 1100, height: 760 },
  });
  page.on("pageerror", (error) => console.error(error.message));
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.waitForFunction(() => !!window.reader);
  const mount = async () => {
    await page.evaluate(
      (id) => window.reader.mount(id, document.getElementById("reader")),
      book,
    );
    await page.waitForTimeout(350);
    return page.frames().find((frame) => frame.url().startsWith("blob:"));
  };
  const firstVisible = (frame) =>
    frame.evaluate(() => {
      const walker = document.createTreeWalker(
        document.body,
        NodeFilter.SHOW_TEXT,
      );
      let node;
      while ((node = walker.nextNode())) {
        if (!node.textContent.trim() || !node.parentElement.closest("p"))
          continue;
        const range = document.createRange();
        range.selectNodeContents(node);
        if (
          ![...range.getClientRects()].some(
            (rect) => rect.bottom > 0 && rect.top < innerHeight,
          )
        )
          continue;
        for (let offset = 0; offset < node.length; offset++) {
          range.setStart(node, offset);
          range.setEnd(node, offset + 1);
          const rect = range.getBoundingClientRect();
          if (rect.bottom > 0 && rect.top < innerHeight)
            return {
              id: node.parentElement.closest("p").id,
              offset,
              top: rect.top,
            };
        }
      }
    });
  let frame = await mount();
  await frame.evaluate(() => {
    const target = document.getElementById("paragraph-60");
    scrollTo(0, scrollY + target.getBoundingClientRect().top + 113);
  });
  await page.waitForTimeout(700);
  const before = await firstVisible(frame);
  await page.evaluate(() => window.reader.unmount());
  frame = await mount();
  const after = await firstVisible(frame);
  console.log(
    JSON.stringify({ scenario: "same layout", before, after }, null, 2),
  );
  assert.equal(after.id, before.id, "reopening must not skip paragraphs");
  assert.ok(
    Math.abs(after.offset - before.offset) <= 2,
    "reopening must preserve the first visible line",
  );
  assert.ok(
    Math.abs(after.top - before.top) <= 2,
    "reopening must preserve the viewport offset",
  );
  const bookmark = await page.evaluate(() => window.reader.currentLocator());
  assert.ok(bookmark.locations.bookrushViewport);
  for (let attempt = 0; attempt < 3; attempt++) {
    await page.evaluate(() => window.reader.unmount());
    frame = await mount();
    assert.deepEqual(
      await firstVisible(frame),
      before,
      "repeated reopen must not accumulate drift",
    );
  }
  await page.evaluate(() => window.reader.unmount());
  delete saved.locations.bookrushViewport;
  frame = await mount();
  const legacy = await firstVisible(frame);
  assert.equal(
    legacy.id,
    before.id,
    "legacy progress must be replayed after typography settles",
  );
  assert.ok(
    Math.abs(legacy.offset - before.offset) <= 2,
    "legacy ratio must not shift by a full screen",
  );
  // Explicit bookmarks use the same anchor after native Readium navigation.
  await frame.evaluate(() =>
    document.getElementById("paragraph-100").scrollIntoView(),
  );
  assert.equal(
    await page.evaluate((value) => window.reader.goToLocator(value), bookmark),
    true,
  );
  assert.deepEqual(
    await firstVisible(frame),
    before,
    "manual bookmark must restore the visible text too",
  );
  // Closing before the SDK's asynchronous progress message must sample the DOM.
  await frame.evaluate(() => {
    const target = document.getElementById("paragraph-85");
    scrollTo(0, scrollY + target.getBoundingClientRect().top + 91);
  });
  const last = await firstVisible(frame);
  await page.evaluate(() => window.reader.unmount());
  frame = await mount();
  assert.deepEqual(
    await firstVisible(frame),
    last,
    "close must capture the actual viewport without waiting for the progress debounce",
  );
  await page.evaluate(() => window.reader.unmount());
  await page.setViewportSize({ width: 460, height: 740 });
  await page.evaluate(() =>
    window.reader.applyPreferences({
      fontFamily: "sans",
      fontSize: 145,
      lineHeight: 2.1,
      textWidth: "wide",
      background: "#f7f4ee",
      foreground: "#29261f",
    }),
  );
  frame = await mount();
  const resized = await firstVisible(frame);
  assert.equal(
    resized.id,
    last.id,
    "a new width/font must keep the same paragraph",
  );
  assert.ok(
    resized.offset <= last.offset && last.offset - resized.offset < 120,
    "reflow may reveal preceding words on the same line, but must not skip ahead",
  );
  console.log(
    JSON.stringify(
      { scenario: "new width and font", before: last, after: resized },
      null,
      2,
    ),
  );
  await page.evaluate(() => window.reader.unmount());
} finally {
  await browser?.close();
  await new Promise((resolve) => server.close(resolve));
}
