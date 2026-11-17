import assert from "node:assert/strict";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { fileURLToPath, URL } from "node:url";
import process from "node:process";
import console from "node:console";
import { build } from "esbuild";
/* global document */

const { chromium } = createRequire(
  new URL("../../../admin-web/package.json", import.meta.url),
)("playwright");
const legacy = await readFile(
  new URL("../../public/pageloop.js", import.meta.url),
  "utf8",
);
const reader = legacy.slice(
  legacy.indexOf("function renderReader("),
  legacy.indexOf("function renderLibrary("),
);
const icons = legacy.slice(
  legacy.indexOf("const ICONS ="),
  legacy.indexOf("function esc("),
);
const css =
  (await readFile(
    new URL("../../public/pageloop.css", import.meta.url),
    "utf8",
  )) +
  (await readFile(
    new URL("../../src/styles/reader-appearance.css", import.meta.url),
    "utf8",
  ));
const bundle = await build({
  stdin: {
    resolveDir: fileURLToPath(new URL("../../", import.meta.url)),
    loader: "ts",
    contents: `
    import { bookmarkDialogs } from './src/reader/bookmarkDialogs.ts';
    import { createBookmarksClient } from './src/reader/bookmarks.ts';
    import { mountReaderPanels } from './src/reader/readerPanels.ts';
    const client = createBookmarksClient((url, init) => fetch(url, init), () => '/api/bookmarks');
    window.__BOOKRUSH_BOOKMARK_DIALOGS__ = bookmarkDialogs;
    window.__BOOKRUSH_API__ = { bookmarks: client.list, bookmark: client.save, renameBookmark: client.rename, deleteBookmark: client.remove, open: async () => {} };
    window.__BOOKRUSH_APPEARANCE__ = {mount() {}, panels: mountReaderPanels};
    window.__BOOKRUSH_READIUM__ = {mount: async () => {}, chapters: async () => [], currentLocator: () => ({href:'/chapter',type:'text/html',locations:{progression:0.42}}), goToLocator: async () => true};
    const state = {readerBook:'book-1',route:'reader'};
    const book = () => ({id:'book-1',title:'Livro de teste'});
    const getMode = () => 'app';
    const esc = value => String(value);
    const toast = value => {window.lastToast = value};
    const shell = content => {document.getElementById('app').innerHTML = content};
    ${icons}
    ${reader}
    renderReader();
  `,
  },
  bundle: true,
  write: false,
  format: "esm",
  platform: "browser",
});
let marks = [];
let failure = 0;
let mutations = 0;
const server = createServer(async (request, response) => {
  response.setHeader("Cache-Control", "no-store");
  if (request.url === "/fixture.js") {
    response.setHeader("Content-Type", "text/javascript");
    response.end(bundle.outputFiles[0].text);
    return;
  }
  if (request.url === "/styles.css") {
    response.setHeader("Content-Type", "text/css");
    response.end(css);
    return;
  }
  if (!request.url.startsWith("/api/")) {
    response.setHeader("Content-Type", "text/html");
    response.end(
      '<html><head><link rel="stylesheet" href="/styles.css"></head><body data-mode="app"><div id="app"></div><script type="module" src="/fixture.js"></script></body></html>',
    );
    return;
  }
  response.setHeader("Content-Type", "application/json");
  if (request.method === "GET") {
    response.end(JSON.stringify(marks));
    return;
  }
  mutations++;
  if (failure) {
    response.statusCode = failure;
    response.end("{}");
    return;
  }
  let body = "";
  for await (const part of request) body += part;
  const input = body ? JSON.parse(body) : {};
  if (request.method === "POST")
    marks = [
      {
        id: "mark-1",
        name: input.name,
        locator: JSON.stringify(input.locator),
      },
    ];
  if (request.method === "PUT") marks[0] = { ...marks[0], name: input.name };
  if (request.method === "DELETE") {
    marks = [];
    response.statusCode = 204;
    response.end();
    return;
  }
  response.end(JSON.stringify(marks[0]));
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
let browser;
try {
  browser = await chromium.launch({
    headless: true,
    executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH,
  });
  const page = await browser.newPage({
    viewport: { width: 1000, height: 760 },
  });
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.waitForFunction(
    () => document.getElementById("bookmarkBtn")?.disabled === false,
  );
  await page
    .getByRole("button", { name: "Marcar posição", exact: true })
    .click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("button", { name: "Salvar", exact: true }).click();
  await page.waitForFunction(() =>
    document.querySelector('[role="alert"]')?.textContent.includes("1 a 120"),
  );
  assert.equal(mutations, 0);
  await dialog.getByLabel("Nome da marcação").fill("Trecho importante");
  failure = 500;
  await dialog.getByRole("button", { name: "Salvar", exact: true }).click();
  await page.waitForFunction(() =>
    document.querySelector('[role="alert"]')?.textContent.includes("HTTP 500"),
  );
  assert.equal(
    await dialog.getByLabel("Nome da marcação").inputValue(),
    "Trecho importante",
  );
  assert.equal(marks.length, 0);
  failure = 0;
  await dialog.getByRole("button", { name: "Salvar", exact: true }).click();
  await dialog.waitFor({ state: "detached" });
  assert.equal(marks[0].name, "Trecho importante");
  const originalLocator = marks[0].locator;
  await page
    .getByRole("button", { name: "Abrir marcações", exact: true })
    .click();
  await page
    .getByRole("button", {
      name: "Editar nome de Trecho importante",
      exact: true,
    })
    .click();
  assert.equal(
    await dialog.getByLabel("Nome da marcação").inputValue(),
    "Trecho importante",
  );
  await dialog.getByLabel("Nome da marcação").fill("Revisar capítulo");
  await dialog.getByRole("button", { name: "Salvar", exact: true }).click();
  await dialog.waitFor({ state: "detached" });
  assert.equal(marks[0].name, "Revisar capítulo");
  assert.equal(marks[0].locator, originalLocator);
  await page
    .getByRole("button", { name: "Excluir Revisar capítulo", exact: true })
    .click();
  assert.equal(
    await dialog
      .getByRole("button", { name: "Cancelar" })
      .evaluate((button) => button === document.activeElement),
    true,
  );
  await dialog.getByRole("button", { name: "Cancelar" }).click();
  await dialog.waitFor({ state: "detached" });
  assert.equal(marks.length, 1);
  await page.screenshot({
    path: fileURLToPath(
      new URL(
        "../../../target/reader-bookmarks-tools/named-bookmark-list.png",
        import.meta.url,
      ),
    ),
  });
  await page
    .getByRole("button", { name: "Excluir Revisar capítulo", exact: true })
    .click();
  failure = 403;
  await dialog.getByRole("button", { name: "Excluir", exact: true }).click();
  await page.waitForFunction(() =>
    document.querySelector('[role="alert"]')?.textContent.includes("HTTP 403"),
  );
  assert.equal(marks.length, 1);
  failure = 0;
  await dialog.getByRole("button", { name: "Excluir", exact: true }).click();
  await dialog.waitFor({ state: "detached" });
  assert.equal(marks.length, 0);
  await page
    .getByText("Você ainda não marcou nenhuma posição neste livro.")
    .waitFor();
  await page
    .getByRole("button", { name: "Marcar posição", exact: true })
    .click();
  await page.keyboard.press("Escape");
  await dialog.waitFor({ state: "detached" });
  assert.equal(marks.length, 0);
  assert.deepEqual(errors, []);
  console.log(
    "Browser bookmarks: create, validation, API retry, rename, cancel, delete and Escape passed.",
  );
} finally {
  await browser?.close();
  await new Promise((resolve) => server.close(resolve));
}
